(ns ^:node-only logseq.db.sqlite.db
  "Sqlite fns for db graphs"
  (:require ["path" :as node-path]
            ["better-sqlite3" :as sqlite3]
            [clojure.string :as string]
            [logseq.db.sqlite.common-db :as sqlite-common-db]
            [logseq.db.sqlite.util :as sqlite-util]
            ;; FIXME: datascript.core has to come before datascript.storage or else nbb fails
            #_:clj-kondo/ignore
            [datascript.core :as d]
            [datascript.storage :refer [IStorage]]
            [goog.object :as gobj]
            [clojure.edn :as edn]))

;; Reference same sqlite default class in cljs + nbb without needing .cljc
(def sqlite (if (find-ns 'nbb.core) (aget sqlite3 "default") sqlite3))

(defn sanitize-db-name
  [db-name]
  (-> db-name
      (string/replace sqlite-util/db-version-prefix "")
      (string/replace "/" "_")
      (string/replace "\\" "_")
      (string/replace ":" "_"))) ;; windows

(defn get-db-full-path
  [graphs-dir db-name]
  (let [db-name' (sanitize-db-name db-name)
        graph-dir (node-path/join graphs-dir db-name')]
    [db-name' (node-path/join graph-dir "db.sqlite")]))

(defn query
  [db sql]
  (let [stmt (.prepare db sql)]
    (.all ^object stmt)))

(defn upsert-addr-content!
  "Upsert addr+data-seq"
  [db data delete-addrs]
  (let [insert (.prepare db "INSERT INTO kvs (addr, content) values (@addr, @content) on conflict(addr) do update set content = @content")
        delete (.prepare db "DELETE from kvs where addr = ?")
        insert-many (.transaction ^object db
                                  (fn [data]
                                    (doseq [item data]
                                      (.run ^object insert item))
                                    (doseq [addr delete-addrs]
                                      (when addr
                                        (.run ^object delete addr)))))]
    (insert-many data)))

(defn restore-data-from-addr
  [db addr]
  (-> (query db (str "select content from kvs where addr = " addr))
      first
      (gobj/get "content")))

(defn new-sqlite-storage
  "Creates a datascript storage for sqlite. Should be functionally equivalent to db-worker/new-sqlite-storage"
  [db]
  (reify IStorage
    (-store [_ addr+data-seq delete-addrs]
      (let [data (->>
                  (map
                   (fn [[addr data]]
                     #js {:addr addr
                          :content (pr-str data)})
                   addr+data-seq)
                  (to-array))]
        (upsert-addr-content! db data delete-addrs)))
    (-restore [_ addr]
      (let [content (restore-data-from-addr db addr)]
        (edn/read-string content)))))

(defn open-db!
  "For a given database name, opens a sqlite db connection for it, creates
  needed sqlite tables if not created and returns a datascript connection that's
  connected to the sqlite db"
  [graphs-dir db-name]
  (let [[_db-sanitized-name db-full-path] (get-db-full-path graphs-dir db-name)
        db (new sqlite db-full-path nil)]
    (sqlite-common-db/create-kvs-table! db)
    (let [storage (new-sqlite-storage db)
          conn (sqlite-common-db/get-storage-conn storage)]
      conn)))

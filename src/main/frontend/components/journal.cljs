(ns frontend.components.journal
  (:require [clojure.string :as string]
            [frontend.components.page :as page]
            [frontend.db :as db]
            [frontend.db-mixins :as db-mixins]
            [frontend.db.model :as model]
            [frontend.handler.page :as page-handler]
            [frontend.state :as state]
            [frontend.ui :as ui]
            [rum.core :as rum]))

(rum/defc journal-cp < rum/reactive
  [title]
  (let [;; Don't edit the journal title
        page (string/lower-case title)
        repo (state/sub :git/current-repo)]
    (page/page {:repo repo
                :page-name page})))

(rum/defc journals < rum/reactive
  [latest-journals]
  [:div#journals
   (ui/infinite-list
    "main-content-container"
    (for [{:block/keys [name]} latest-journals]
      [:div.journal-item.content {:key name}
       (journal-cp name)])
    {:has-more (page-handler/has-more-journals?)
     :more-class "text-4xl"
     :on-top-reached page-handler/create-today-journal!
     :on-load (fn []
                (page-handler/load-more-journals!))})])

(rum/defc all-journals < rum/reactive db-mixins/query
  []
  (let [journals-length (state/sub :journals-length)
        latest-journals (db/get-latest-journals (state/get-current-repo) journals-length)]
    (journals latest-journals)))

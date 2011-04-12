(ns my-share.core
  (:use [compojure.core]
	[ring.adapter.jetty]
	[ring.util.response]
	[net.cgrand.moustache]
	[hiccup core page-helpers form-helpers])
  (:require [compojure.route :as route]
	    [ring.middleware.multipart-params :as mp]
	    [clojure.contrib.duck-streams :as ds]
	    [clojure.contrib.io :as io]
	    [clojure.contrib.json :as json]
	    [clojure.contrib.java-utils :as utils]
	    [com.draines.postal.core :as mail])
  (:import (java.util UUID)
	   (java.io.BufferedInputStream)
	   (java.io.FileInputStream))
  (:gen-class))

(def domain "127.0.0.1:8080")
(def upload-dir "files")
(def from-address "corey@hoffstein.com")
(def mail-host "mail.hoffstein.com")

(defn- generate-uuid [] (.toString (UUID/randomUUID)))

(defn- html-doc [title & body]
  (html
   (doctype :html4)
   [:html
    [:head
     [:title title]]
      [:body
       body]]))

(defn- upload-form [& message]
  (html-doc "Upload"
	    (when (not (nil? message)) [:br] [:b message])
	    [:form {:action "/upload" :method "post" :enctype "multipart/form-data"}
	     (label :to "To: ")
	     (text-field {:size 50} :to)
	     [:br]
	     (label :file "File: ")
	     (file-upload :file)
	     [:br]
	     (label :erase-on-download? "Remove on Download? ")
	     (check-box :erase-on-download? true)
	     [:br]
	     (submit-button "Upload")]))

(defn- email-body [uuid]
  (str "Hey, I just uploaded a private file for you.  To retrieve it, please click " domain "/" uuid))

(defn- post-form [request]
  (let [form-params (:multipart-params  request)
	file (get form-params "file")
	email-address (get form-params "to")
	uuid (generate-uuid)
	content-type (:content-type file)
	file-name (:filename file)
	file-size (:size file)
	meta-file (ds/file-str (str upload-dir "/" uuid ".meta"))
	out-file (ds/file-str (str upload-dir "/" uuid ".file"))
	erase-on-download? (get form-params "erase-on-download?")
	mail-status (mail/send-message #^{:host mail-host}
				       {:from from-address
					:to email-address
					:subject "Private File Upload For You"
					:body (email-body uuid)})
	_ (println mail-status)]
    (if (= (:code mail-status) 0)
      (do
	(ds/spit meta-file (json/json-str {:filename file-name
					   :content-type content-type
					   :size file-size
					   :erase-on-download? erase-on-download?}))
	(ds/copy (:tempfile file) out-file)
	(response (upload-form "File Upload Successful")))
      (response (upload-form "File Upload Failed -- Could Not Send E-Mail")))))

(defn handle-request [handle]
  (let [in-file (ds/file-str (str upload-dir "/" handle ".file"))
	meta-file (ds/file-str (str upload-dir "/" handle ".meta"))
	meta-data (json/read-json (slurp meta-file))
	buffered-input-stream (java.io.BufferedInputStream. (java.io.FileInputStream. in-file))
	remove-file? (:erase-on-download? meta-data)]
    (do
      (when remove-file?
	(utils/delete-file meta-file)
	(utils/delete-file in-file))
      {:status 200
       :headers {"content-type" (:content-type meta-data)
		 "content-disposition" (str "attachment; filename=" (:filename meta-data))}
       :body buffered-input-stream})))
    

(def main-routes
     (app
      ["upload"] {:get (fn [req] (response (upload-form)))
		  :post (mp/wrap-multipart-params post-form)}
      [_] {:get (fn [req] (handle-request (apply str (rest (:uri req)))))}))
      
(defn -main [& args]
  (run-jetty #'main-routes {:port 8080 :join? false})) 
(defproject my-share "1.0.0-SNAPSHOT"
  :description "Share large files via a web interface"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
		 [com.draines/postal "1.4.0"]
		 [compojure "0.6.2"]
		 [ring "0.3.7"]
		 [net.cgrand/moustache "1.0.0"]
		 [hiccup "0.3.4"]]
  :dev-dependencies [[swank-clojure "1.3.0-SNAPSHOT"]]
  :main my-share.core
  :aot [my-share.core])

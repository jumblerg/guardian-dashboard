(ns guardian.dashboard.service
  (:require
    [javelin.core :refer [cell cell=]]
    [clojure.set  :refer [rename-keys]]
    [cljsjs.d3]))

;;; config ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def sensors
  {:cpu-power         "Package"
   :cpu-temp          "Package"
   :cpu-load          "UC"
   :gpu-processor     "GPU"
   :gpu-memory        "Memory"
   :gpu-frame-buffer  "Frame Buffer"
   :gpu-video-engine  "Video Engine"
   :gpu-bus-interface "Bus Interface"
   :hdd-temp          "Assembly"
   :zone-1            "THRM"
   :zone-2            "TZ00"
   :zone-3            "TZ01"})

;;; utils ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn hsl->rgb [[h s l]]
  (let [c (.rgb js/d3 (.hsl js/d3 h s l))]
    (mapv int [(.-r c) (.-g c) (.-b c)])))

(defn rgb->hsl [[r g b]]
  (let [c (.hsl js/d3 (.rgb js/d3 r g b))]
    (mapv int [(.-h c) (.-s c) (.-l c)])))

(defn name->sensor [reading]
  (rename-keys reading {:name :sensor}))

(defn get-sensor [coll sensor]
 (-> (some #(when (= (sensor sensors) (:name %)) %) coll)
     (name->sensor)))

;;; xforms ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn cpu [{:keys [name clocks loads temps volts watts]}]
  (let [temps* (remove #(= (:name %) (:cpu-temp sensors)) temps)
        loads* (remove #(= (:name %) (:cpu-load sensors)) loads)
        cores* (mapv #(hash-map :name (:name %) :freq (name->sensor %1) :temp (name->sensor %2)) clocks temps*)]
    {:name  name
     :type  :cpu
     :temp  (get-sensor temps :cpu-temp)
     :load  (get-sensor loads :cpu-load)
     :cores (mapv #(assoc % :threads %2) cores* (partition 2 loads*))}))

(defn drive [{:keys [name loads temps]}]
  {:name   name
   :type   :hdd
   :volume (-> loads first :name)
   :used   (-> loads first name->sensor)
   :free   (-> loads first name->sensor (update :value (partial - 100)))
   :temp   (get-sensor temps :hdd-temp)})

(defn card [{:keys [name loads temps]}]
  {:name          name
   :type          :gpu
   :gpu           {:name "GPU"
                   :temp (get-sensor loads :gpu-processor)
                   :load (get-sensor temps :gpu-processor)}
   :memory        {:name "Memory"
                   :load (get-sensor loads :gpu-memory)}
   :frame-buffer  {:name "Frame Buffer"
                   :load (get-sensor loads :gpu-frame-buffer)}
   :video-engine  {:name "Video Engine"
                   :temp (get-sensor loads :gpu-video-engine)}
   :bus-interface {:name "Bus Interface"
                   :temp (get-sensor loads :gpu-bus-interface)}})

(defn keyboard [keyboard]
  (let [zone #(hash-map
                 :name  (->> % :zone (str "Zone "))
                 :color (->> % :color rgb->hsl))
        zones (->> (dissoc keyboard :all)
                   (sort first)
                   (mapv (comp zone second)))]
    {:name  "Keyboard"
     :type  :kb
     :zones zones}))

(defn memory [{:keys [name free total] :as memory}]
  {:name "Memory"
   :type :memory
   :used {:value (- total free)}
   :free {:value free}})

(defn motherboard [{{:keys [name temps]} :mb mem :memory kb :led_keyboard :keys [cpus gpus hdds]}]
  {:name     name
   :type     :mb
   :zone-1   {:name "CPU Thermal Zone"
              :desc "Located next to the CPU core on Intel boards"
              :temp (get-sensor temps :zone-1)}
   :zone-2   {:name "North Bridge Thermal Zone"
              :desc "Located next to the CPU socket on Intel boards"
              :temp (get-sensor temps :zone-2)}
   :zone-3   {:name "South Bridge Thermal Zone"
              :desc "Located next to the memory slots on Intel boards"
              :temp (get-sensor temps :zone-3)}
   :cpu       (cpu     (first cpus))
   :gpu       {:name (-> gpus first :name)}
   :memory   (memory mem)
   :keyboard (keyboard kb)
   :cards    (mapv card  (rest gpus))
   :drives   (mapv drive       hdds)})

(defn buffer-sensor-data [data]
  (let [data (motherboard data)]
    (assoc (dissoc data :cards :drives) :views (apply concat ((juxt :cards :drives) data)))))

(defn device-data [data]
  (prn :device-data data)
  data)


;;; api ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn connect [url]
  (let [conn (cell (js/WebSocket. url))]
       (cell= (set! (.-onclose conn) ~(fn [_] (reset! conn (js/WebSocket. url)))))
    (-> (fn [resolve reject]
          (set! (.-onopen  @conn) #(resolve conn))
          (set! (.-onerror @conn) #(reject %)))
        (js/Promise.))))

(defn- call [tag conn & kwargs]
  (let [data (apply hash-map kwargs)]
    (->> {:tag tag :data data} (clj->js) (.stringify js/JSON) (.send @conn))))

(defn bind-sensors! [conn state error & [poll-freq hist-max]]
  (let [cljs #(js->clj % :keywordize-keys true)
        data #(-> % .-data js/JSON.parse cljs :data)]
  (cell= (set! (.-onmessage conn) ~(fn [e] (reset! state (buffer-sensor-data (data e))))))
  (cell= (set! (.-onerror   conn) ~(fn [e] (reset! error e))))
  (.setInterval js/window call (or poll-freq 1000) "get_sensors" conn)))

(defn get-devices [conn]
  (device-data (call "get_devices" conn)))

(defn set-keyboard-zone! [conn zone color]
  (call "set_keyboard_zones" conn :name "static_color" :zone (str zone) :color (hsl->rgb color)))

(defn set-plugin-effect! [conn effect]
  (call "set_plugin_effect" conn :name "static_color" :effect effect))
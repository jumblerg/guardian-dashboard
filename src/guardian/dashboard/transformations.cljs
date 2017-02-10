(ns guardian.dashboard.transformations
  (:require
    [clojure.set :refer [rename-keys]]
    [cljsjs.d3]))

;;; utils ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn hsl->rgb [[h s l]]
  (let [c (.rgb js/d3 (.hsl js/d3 h s l))]
    (mapv int [(.-r c) (.-g c) (.-b c)])))

(defn rgb->hsl [[r g b]]
  (let [c (.hsl js/d3 (.rgb js/d3 r g b))]
    (mapv int [(.-h c) (.-s c) (.-l c)])))

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
     :temp  (get-sensor temps :cpu-temp)
     :load  (get-sensor loads :cpu-load)
     :cores (mapv #(assoc % :threads %2) cores* (partition 2 loads*))}))

(defn drive [{:keys [name loads temps]}]
  {:name   name
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
     :zones zones}))

(defn memory [{:keys [name free total] :as memory}]
  {:name "Memory"
   :used {:value (- total free)}
   :free {:value free}})

(defn motherboard [{{:keys [name temps]} :mb mem :memory kb :led_keyboard :keys [cpus gpus hdds]}]
  {:name   name
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

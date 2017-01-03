(ns ^{:hoplon/page "index.html"} guardian.dashboard
  (:refer-clojure
    :exclude [-])
  (:require
    [adzerk.env                         :as e]
    [guardian.dashboard.visualizations  :as v]
    [guardian.dashboard.transformations :as x]
    [cljs.pprint     :refer [pprint]]
    [javelin.core    :refer [defc defc= cell= cell cell-let with-let]]
    [hoplon.core     :refer [defelem for-tpl when-tpl case-tpl]]
    [hoplon.ui       :refer [elem image window video s b]]
    [hoplon.ui.attrs :refer [- r d rgb hsl lgr]]))

;;; environment ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(enable-console-print!)

(e/def URL "ws://localhost:8000")

(def hist-max 80)

;;; utils ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->GB [bytes] (when bytes (str (.toFixed (/ bytes 1000000000) 2) "GB")))
(defn ->% [num]    (when num (str (.toFixed num) "%")))
(defn safe-name [keyword] (try (name keyword) (catch js/Error _)))

(defn rect    [e] (.getBoundingClientRect (.-currentTarget e)))
(defn mouse-x [e] (- (.-pageX e) (.-left (rect e))))
(defn mouse-y [e] (- (.-pageY e) (.-top  (rect e))))

;;; content ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def footer-menu-items
  [["facebook-icon.svg"  "https://facebook.com"]
   ["instagram-icon.svg" "https://www.instagram.com/xoticpc/"]
   ["twitter-icon.svg"   "https://twitter.com/XoticPC"]
   ["youtube-icon.svg"   "https://www.youtube.com/channel/UCJ9O0vRPsMFk5UtIDimr6hQ"]])

;;; data-models ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defc state {:view :mb :index 0 :hist #queue[]})
(defc error nil)

;;; queries ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defc= hist  (-> state :hist))
(defc= data  (-> hist  last) #(swap! state update :hist (fn [h] (conj (if (> (count h) hist-max) (pop h) h) %))))
(defc= view  (-> state :view))

(defc= hist-model (mapv #(get-in % [:components (:index state 0)]) hist))
(defc= data-model (-> hist-model last))

#_(cell= (prn :hist-model hist-model))
#_(cell= (prn :data-model data-model))

;;; service ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn connect [url state error]
  (let [conn (js/WebSocket. url)
        cljs #(js->clj % :keywordize-keys true)
        data #(-> % .-data js/JSON.parse cljs :data x/xform)]
    (-> (fn [resolve reject]
          (set! (.-onopen    conn) #(resolve conn))
          (set! (.-onerror   conn) #(reject (reset! error %)))
          (set! (.-onmessage conn) #(reset! state (data %))))
        (js/Promise.))))

(defn call [tag conn data]
  (->> {:tag tag :data data} (clj->js) (.stringify js/JSON) (.send conn)))

(defn poll [tag conn data & [interval]]
  (.setInterval js/window call (or interval 1000) tag conn data))

(def sub-hardware-data (partial poll "get_monitor_data"))
(def get-smart-data    (partial call "get_smart_data"))
(def set-client-data   (partial call "set_client_data"))

;;; commands ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-kb-color! [zone color]
  (prn :changing-color color)
  #_(set-client-data zone color))

(defn change-state! [view & [index]]
  (swap! state assoc :view view :index index))

(defn change-route! [[[view {:keys [index]}] _]]
  (change-state! view index))

(defn initiate! [[path qmap] status _]
  (-> (connect URL data error)
      (.then  #(sub-hardware-data %))
      (.catch #(.log js/console "error: " %))))

;;; styles ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;-- breakpoints ---------------------------------------------------------------;

(def sm 760)
(def md 1240)
(def lg 1480)

(defn >sm [& bks] (apply b (r 1 1) sm bks))

;-- sizes ---------------------------------------------------------------------;

(def l    2)
(def g-sm 6)
(def g-lg 16)

;-- colors --------------------------------------------------------------------;

(def white   (rgb 0xFAFAFA))
(def red     (rgb 0xCC181E))
(def yellow  (rgb 0xFFD200))
(def grey-1  (rgb 0x777777))
(def grey-2  (rgb 0x555555))
(def grey-3  (rgb 0x414141))
(def grey-4  (rgb 0x333333))
(def grey-5  (rgb 0x202020))
(def grey-6  (rgb 0x161616))
(def black   (rgb 0x181818))

(defn temp->color [t]
  (let [h (-> (+ 20 t) (* 240) (/ 60) (- 20))]
    (hsl h (r 7 10) (r 1 2))))

;-- typography ----------------------------------------------------------------;

(def font-1     {:f 21 :ff ["MagistralC Bold" :sans-serif] :fc white})
(def font-2     {:f 18 :ff ["MagistralC Bold" :sans-serif] :fc white})
(def font-3     {:f 16 :ff ["MagistralC Bold" :sans-serif] :fc white})
(def font-4     {:f 14 :ff ["MagistralC Bold" :sans-serif] :fc white})
(def font-label {:f 14 :ff ["Lato Semibold"   :sans-serif] :fc black})
(def font-body  {:f 12 :ff ["Lato Medium"     :sans-serif] :fc black})

;-- controls ------------------------------------------------------------------;

(defelem primary-button [attrs elems]
  (elem font-label :pv 6 :ph 12 :c red :r 6
    attrs elems))

(defelem secondary-button [attrs elems]
  (elem font-label :pv 4 :ph 10 :c grey-5 :r 6
    attrs elems))

(defelem color-slider [{:keys [sh sv s angle color changed] :as attrs} elems]
  (let [size  (cell= (case angle 90 (or sh s) 180 (or sv s) 270 (or sh s) (or sv s)))
        pos   (cell  (/ @size (* (/ @color 360) @size)))
        color (cell= (hsl (* (/ pos size) 360) (r 1 1) (r 1 2)))]
    (cell= (when changed (changed color)))
    (elem :pt pos :c (apply lgr angle (map #(hsl % (r 1 1) (r 1 2)) (range 0 360 10))) :click #(reset! pos (.parseInt (mouse-y %)))
      (elem :s 20 :r 10 :c color :b 2 :bc white :m :pointer)
      (dissoc attrs :angle :color :changed) elems)))

;;; views ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defelem panel [{:keys [icon name] :as attrs} elems]
  (elem (dissoc attrs :icon :name) :fc grey-1
    (elem :sh (r 1 1) :c grey-4 :p g-lg :gh g-lg
      (image :sv 40 :av :mid :url icon)
      (elem :sv 40 :av :mid
        name))
    (elem :sh (r 1 1) :sv (- (r 1 1) 40 g-lg) :pv g-sm :ph g-lg :c grey-5
      elems)))

(defelem panel-table [{:keys [name] :as attrs} elems]
  (elem (dissoc attrs :name)
    (elem :sh (r 1 1) :p g-sm :ah :mid
      name)
    elems))

(defelem panel-row [{:keys [name] :as attrs} elems]
  (elem :fc grey-4 :pv g-sm (dissoc attrs :name)
    (elem :sh (r 1 2)
      name)
    (elem :sh (r 1 2) :ah :end
      elems)))

(defelem card [{:keys [name icon] :as attrs} elems]
  (elem (dissoc attrs :icon)
    (elem font-3 :sh (r 1 1) :p 8
      name)
    (image :p 28 :sh (r 1 1) :a :mid :c grey-5 :url icon)
    (elem font-3 :p 6 :sh (r 1 1) :sv (r 1 5) :a :mid :c grey-3 :fc (white :a 0.5)
      elems)))

(defelem title [{:keys [name] :as attrs} elems]
  (elem font-1 :sh (r 1 1) :g g-sm :av :end
      (elem font-1
        name)
      (elem font-4 :fc red
        elems)))

(defn mb-view []
  (list
    (title :name (cell= (:name data-model))
      "Motherboard")
    (elem :g g-lg ;; remove after merging opts with vflatten
      (for-tpl [{:keys [name value]} (cell= (:temps data-model))]
        (card :sh 100 :name name :icon "mb-icon.svg"
          (cell= (str value "° C")))))
    (elem :g g-lg ;; remove after merging apts with vflatten
      (for-tpl [{:keys [name value]} (cell= (:fans data-model))]
        (card :sh 100 :name name :icon "mb-icon.svg"
          (cell= (str value "RPM")))))))

(defn cpu-view []
  (list
    (title :name (cell= (:name data-model))
      "CPU")
    (v/hist-chart font-4 :sh (- (r 1 1) 300 g-lg) :sv 300 :c grey-4 :b 10 :bc grey-5 :fc (white :a 0.5)
      :domain (cell= (mapv #(hash-map :value (* (/ (-> % :load :value) 100) 300) :color (-> % :temp :value temp->color)) hist-model))
      :range  [{:color :blue}])
    (elem :s 300 :c grey-4 :b 10 :bc grey-5
       (for-tpl [{:keys [name temp threads]} (cell= (:cores data-model))]
         (elem :sh (cell= (r 1 (count (:cores data-model)))) :sv (r 1 1) :gh 8 :ah :mid :av :end
           (for-tpl [{:keys [name load]} threads]
             (elem :sh 4 :sv (cell= (+ (* load 3) 6)) :r 6 :c (cell= (temp->color temp))))))) ;; can't use ratio because of https://github.com/hoplon/ui/issues/25
    (elem :g g-lg :av :end ;; remove after merging opts with vflatten
      (for-tpl [{:keys [name value]} (cell= (:volts data-model))]
        (card :sh 100 :name name :icon "mb-icon.svg"
          (cell= (str value "V")))))))

(defn gpu-view []
  (list
    (title :name (cell= (:name data-model))
      "GPU")
    (elem :g g-lg :av :end ;; remove after merging opts with vflatten
      (for-tpl [{:keys [name value]} (cell= (:loads data-model))]
        (card :sh 100 :name name :icon "mb-icon.svg"
          (cell= (str value "%")))))))

(defn memory-view []
  (list
    (title :name (cell= (:name data-model))
      "Memory")
    (v/dist-chart font-4 :sh (r 1 1) :sv 100 :c grey-5 :fc (white :a 0.5)
      :domain (cell= [{:label (->GB (:used data-model)) :value (:used data-model)} {:label (->GB (:free data-model)) :value (:free data-model)}])
      :range  [{:color :green} {:color grey-4}])
    (v/hist-chart font-4 :sh (r 1 1) :sv 400 :c grey-5 :fc (white :a 0.5)
      :domain (cell= (mapv #(hash-map :label "" :value (* (/ (:used %) (:free %)) 400)) hist-model))
      :range  [{:color :green}])))

(defn hdd-view []
  (list
    (title :name (cell= (:name data-model))
      "Hard Drive")
    (v/dist-chart font-4 :sh (r 1 1) :sv 100 :c grey-5 :fc (white :a 0.5)
      :domain (cell= [{:label (->% (:used data-model)) :value (:used data-model)} {:label (->% (:free data-model)) :value (:free data-model)}])
      :range  [{:color :green} {:color grey-4}])
    (elem :sh (r 1 1) :sv 300 :c grey-4 :b 10 :bc grey-5)
    (card :sh 100 :name (cell= (-> data-model :temp :name)) :icon "mb-icon.svg"
      (cell= (-> data-model :temp :value (str "° C"))))))

(defn keyboard-view []
  (list
    (title :name (cell= (:name data-model))
      "Keyboard")
    (elem :sh (r 1 1) :p 50 :g 50
      (for-tpl [{:keys [id name color]} (cell= (:zones data-model))]
       (elem :ah :mid :gv 20
         (color-slider :sh 20 :sv 300 :r 10 :color color :angle 180 :changed #(set-kb-color! id %))
         (elem font-4 :sh (r 1 1) :ah :mid
           name))))))

(window
  :title        "Xotic"
  :route        (cell= [[view]])
  :initiated    initiate!
  :routechanged change-route!
  :c grey-4 :g l
  (elem :sh (r 1 1) :ah :mid :c black
    (elem :sh (r 1 1) :ah (b :mid sm :beg) :av (b :beg sm :mid) :p g-lg :gv g-lg
      (image :sh 200 :url "xotic-pc-logo.svg" :m :pointer :click #(.open js/window "https://www.xoticpc.com"))
      (elem :sh (>sm (- (r 1 1) 200)) :ah (b :mid sm :end) :gh (* 2 g-lg)
        (for [[logo link] footer-menu-items]
          (image :m :pointer :url logo :click #(.open js/window link))))))
  (elem :sh (r 1 1) :sv (- (r 1 1) 80) :g l
    (elem :sh (>sm 80 md 380) :sv (r 1 1) :gv l
      (for-tpl [[idx {:keys [name type]}] (cell= (map-indexed vector (:components data)))]
        (let [selected (cell= (= idx (:index state)))]
          (elem font-4 :sh (r 1 1) :s 80 :ph g-lg :gh g-lg :ah (b :mid md :beg) :av :mid :c (cell= (if selected grey-4 grey-5)) :fc (cell= (if selected white grey-1)) :bl 2 :bc (cell= (if selected red grey-5)) :m :pointer :click #(change-state! @type @idx)
            (image :s 34 :a :mid :url (cell= (when type (str (safe-name type) "-icon.svg"))))
            (when-tpl (b true sm false md true)
              (elem :sh (b 300 sm (- (r 1 1) 34 g-lg))
                name)))))
      (b nil sm (elem :sh (>sm 80 md 380) :sv (r 2 1) :c grey-6)))
    (elem :sh (>sm (- (r 1 1) 80 l) md (- (r 1 1) 380 l)) :sv (r 2 1) :p g-lg :g g-lg :c grey-6
      (case-tpl view
        :mb       (mb-view)
        :cpu      (cpu-view)
        :gpu      (gpu-view)
        :memory   (memory-view)
        :hdd      (hdd-view)
        :keyboard (keyboard-view)))))

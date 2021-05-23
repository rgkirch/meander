(ns meander.zeta-test
  (:require [clojure.test :as t]
            [meander.zeta :as m]))

;; (t/deftest query-test
;;   (t/is (= {}
;;            ((m/query-one 1) 1)))

;;   (t/is (= {}
;;            (^{::m/optimize-on-construct? true
;;               ::m/optimize-post-construct? true}
;;             (m/query-one (m/one [1] [2])) [2])))

;;   (t/is (= {'?a 1}
;;            ((m/query-one (m/one ?a ?b)) 1)))

;;   (t/is (= [{}]
;;            ((m/query-all 1) 1)))

;;   (t/is (= nil
;;            ((m/query-one 1) 2)))

;;   (t/is (= ()
;;            ((m/query-all 1) 2)))

;;   (t/is (= {'?a 1}
;;            ((m/query-one ?a) 1)))

;;   (t/is (= [{'?a 1}]
;;            ((m/query-all ?a) 1)))

;;   (t/is (= {'?a 1}
;;            ((m/query-one [?a]) [1])))

;;   (t/is (= nil
;;            ((m/query-one [?a]) [])))

;;   (t/is (= {'?b 1}
;;            ((m/query-one (m/one [?a 1] [?b 2])) [1 2])))

;;   (let [one-optimized (m/query-one [?a ?a])
;;         one-not-optimized ^{::m/optimize-on-construct? false
;;                             ::m/optimize-post-construct? false} (m/query-one [?a ?a])
;;         all-optimized (m/query-all [?a ?a])
;;         all-not-optimized ^{::m/optimize-on-construct? false
;;                             ::m/optimize-post-construct? false} (m/query-all [?a ?a])]
;;     (t/testing "logic pair pass (one, not optimized)"
;;       (t/is (= {'?a 1}
;;                (one-not-optimized [1 1]))))

;;     (t/testing "logic-pair pass (one, optimized)"
;;       (t/is (= {'?a 1}
;;                (one-optimized [1 1]))))

;;     (t/testing "logic pair pass (all, not optimized)"
;;       (t/is (= [{'?a 1}]
;;                (all-not-optimized [1 1]))))

;;     (t/testing "logic pair pass (all, optimized)"
;;       (t/is (= [{'?a 1}]
;;                (all-optimized [1 1]))))

;;     (t/testing "logic pair fail (one, not optimized)"
;;       (t/is (= nil
;;                (one-not-optimized [1 2]))))

;;     (t/testing "logic pair fail (one, optimized)"
;;       (t/is (= nil
;;                (one-optimized [1 2]))))

;;     (t/testing "logic pair fail (all, optimized)"
;;       (t/is (= ()
;;                (all-optimized [1 2]))))))

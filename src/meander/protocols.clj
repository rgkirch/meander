(ns meander.protocols)


(defprotocol IForm
  (-form [x]))


(defprotocol ITermSize
  (-term-size [t]))


(defprotocol ITermPositions
  (-term-positions [t level]))


(defprotocol ITermVariables
  (-term-variables [t]))


(defprotocol IRule)


(defprotocol IRuleLeftHandSide
  (-rule-left-hand-side [r]))


(defprotocol IRuleRightHandSide
  (-rule-right-hand-side [r]))


(defprotocol IVariable)


(defprotocol IUnify
  (-unify [this that substitution-map]))


(defprotocol IUnify*
  (-unify* [this that substition-map]))


(defprotocol ISubstitute
  (-substitute [this substitution-map]))


(defprotocol IFmap
  (-fmap [this f]))


(defprotocol IWalk
  (-walk [this inner-f outer-f]))


(defprotocol IStream
  (-stream-head [this no-head])
  (-stream-tail [this]))

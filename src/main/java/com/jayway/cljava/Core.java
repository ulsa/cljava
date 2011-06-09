package com.jayway.cljava;

import static clojure.lang.LockingTransaction.runInTransaction;
import static clojure.lang.Numbers.add;
import static clojure.lang.Numbers.dec;
import static clojure.lang.Numbers.equiv;
import static clojure.lang.Numbers.gt;
import static clojure.lang.Numbers.isPos;
import static clojure.lang.Numbers.lt;
import static clojure.lang.RT.assoc;
import static clojure.lang.RT.cons;
import static clojure.lang.RT.count;
import static clojure.lang.RT.dissoc;
import static clojure.lang.RT.first;
import static clojure.lang.RT.list;
import static clojure.lang.RT.more;
import static clojure.lang.RT.next;
import static clojure.lang.RT.seq;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

import clojure.lang.AFn;
import clojure.lang.Agent;
import clojure.lang.IFn;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.LazilyPersistentVector;
import clojure.lang.MapEntry;
import clojure.lang.PersistentList;
import clojure.lang.RT;
import clojure.lang.Util;

/**
 * Useful Clojure functions implemented in Java.
 * <ul>
 * <li>filter
 * <li>map
 * <li>reduce
 * <li>range
 * <li>conj
 * <li>partition
 * <li>drop
 * <li>take
 * <li>max
 * <li>min
 * <li>apply
 * <li>list*
 * </ul>
 *
 * @author Ulrik Sandberg
 */
public class Core {
	public static final Agent agent(Object state) {
		try {
			return new Agent(state);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public static final IFn seq = new AFn() {
		@Override
		public Object invoke(Object arg1) throws Exception {
			return seq(arg1);
		}
	};
	public static final IFn first = new AFn() {
		public Object invoke(Object arg1) throws Exception {
			return first(arg1);
		};
	};
	public static final IFn rest = new AFn() {
		public Object invoke(Object arg1) throws Exception {
			return more(arg1);
		};
	};
	public static final IFn identity = new AFn() {
		@Override
		public Object invoke(Object arg1) throws Exception {
			return arg1;
		}
	};
	private static final Comparator<Object> compare = new Comparator<Object>() {
		public int compare(Object x, Object y) {
			return Util.compare(x, y);
		};
	};

	//	(defn filter
	//	  "Returns a lazy sequence of the items in coll for which
	//	  (pred item) returns true. pred must be free of side-effects."
	//	  {:added "1.0"}
	//	  ([pred coll]
	//	   (lazy-seq
	//	    (when-let [s (seq coll)]
	//	      (if (chunked-seq? s)
	//	        (let [c (chunk-first s)
	//	              size (count c)
	//	              b (chunk-buffer size)]
	//	          (dotimes [i size]
	//	              (when (pred (.nth c i))
	//	                (chunk-append b (.nth c i))))
	//	          (chunk-cons (chunk b) (filter pred (chunk-rest s))))
	//	        (let [f (first s) r (rest s)]
	//	          (if (pred f)
	//	            (cons f (filter pred r))
	//	            (filter pred r))))))))
	public static ISeq filter(IFn pred, Object coll) {
		try {
			ISeq s = seq(coll);
			if (s != null) {
				Object f = s.first();
				ISeq r = s.more();
				if ((Boolean) pred.invoke(f))
					return cons(f, filter(pred, r));
				else
					return filter(pred, r);
			}
			return null;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//	(defn map
	//	  "Returns a lazy sequence consisting of the result of applying f to the
	//	  set of first items of each coll, followed by applying f to the set
	//	  of second items in each coll, until any one of the colls is
	//	  exhausted.  Any remaining items in other colls are ignored. Function
	//	  f should accept number-of-colls arguments."
	//	  {:added "1.0"}
	//	  ([f coll]
	//	   (lazy-seq
	//	    (when-let [s (seq coll)]
	//	      (if (chunked-seq? s)
	//	        (let [c (chunk-first s)
	//	              size (int (count c))
	//	              b (chunk-buffer size)]
	//	          (dotimes [i size]
	//	              (chunk-append b (f (.nth c i))))
	//	          (chunk-cons (chunk b) (map f (chunk-rest s))))
	//	        (cons (f (first s)) (map f (rest s)))))))
	//	  ([f c1 c2]
	//	   (lazy-seq
	//	    (let [s1 (seq c1) s2 (seq c2)]
	//	      (when (and s1 s2)
	//	        (cons (f (first s1) (first s2))
	//	              (map f (rest s1) (rest s2)))))))
	//	  ([f c1 c2 c3]
	//	   (lazy-seq
	//	    (let [s1 (seq c1) s2 (seq c2) s3 (seq c3)]
	//	      (when (and  s1 s2 s3)
	//	        (cons (f (first s1) (first s2) (first s3))
	//	              (map f (rest s1) (rest s2) (rest s3)))))))
	//	  ([f c1 c2 c3 & colls]
	//	   (let [step (fn step [cs]
	//	                 (lazy-seq
	//	                  (let [ss (map seq cs)]
	//	                    (when (every? identity ss)
	//	                      (cons (map first ss) (step (map rest ss)))))))]
	//	     (map #(apply f %) (step (conj colls c3 c2 c1))))))
	public static ISeq map(IFn f, Object coll) {
		try {
			ISeq s = seq(coll);
			if (s != null) {
				return cons(f.invoke(s.first()), map(f, s.next()));
			}
			return null;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	//	  ([f c1 c2]
	//	   (lazy-seq
	//	    (let [s1 (seq c1) s2 (seq c2)]
	//	      (when (and s1 s2)
	//	        (cons (f (first s1) (first s2))
	//	              (map f (rest s1) (rest s2)))))))
	public static ISeq map(IFn f, Object c1, Object c2) {
		try {
			ISeq s1 = seq(c1);
			ISeq s2 = seq(c2);
			if (s1 != null && s2 != null) {
				return cons(f.invoke(s1.first(), s2.first()), map(f, s1.next(), s2.first()));
			}
			return null;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	//	  ([f c1 c2 c3]
	//	   (lazy-seq
	//	    (let [s1 (seq c1) s2 (seq c2) s3 (seq c3)]
	//	      (when (and  s1 s2 s3)
	//	        (cons (f (first s1) (first s2) (first s3))
	//	              (map f (rest s1) (rest s2) (rest s3)))))))
	public static ISeq map(IFn f, Object c1, Object c2, Object c3) {
		try {
			ISeq s1 = seq(c1);
			ISeq s2 = seq(c2);
			ISeq s3 = seq(c3);
			if (s1 != null && s2 != null && s3 != null) {
				return cons(f.invoke(s1.first(), s2.first(), s3.first()), map(f, s1.next(), s2.first(), s3.first()));
			}
			return null;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	//	  ([f c1 c2 c3 & colls]
	//	   (let [step (fn step [cs]
	//	                 (lazy-seq
	//	                  (let [ss (map seq cs)]
	//	                    (when (every? identity ss)
	//	                      (cons (map first ss) (step (map rest ss)))))))]
	//	     (map #(apply f %) (step (conj colls c3 c2 c1))))))
	public static ISeq map(final IFn f, Object c1, Object c2, Object c3, Object... colls) {
		IFn applyF = new AFn() {
			@Override
			public Object invoke(Object arg1) throws Exception {
				ISeq s = seq(arg1);
				return f.applyTo(s);
			}
		};
		return map(applyF, mapStep(conj(new PersistentList(colls), c3, c2, c1)));
	}
	private static ISeq mapStep(Object cs) {
		ISeq ss = map(seq, cs);
		if (isEvery(identity, ss))
			return cons(map(first, ss), mapStep(map(rest, ss)));
		return null;
	}

	//	(defn merge-with
	//	  "Returns a map that consists of the rest of the maps conj-ed onto
	//	  the first.  If a key occurs in more than one map, the mapping(s)
	//	  from the latter (left-to-right) will be combined with the mapping in
	//	  the result by calling (f val-in-result val-in-latter)."
	//	  {:added "1.0"}
	//	  [f & maps]
	//	  (when (some identity maps)
	//	    (let [merge-entry (fn [m e]
	//				(let [k (key e) v (val e)]
	//				  (if (contains? m k)
	//				    (assoc m k (f (get m k) v))
	//				    (assoc m k v))))
	//	          merge2 (fn [m1 m2]
	//			   (reduce merge-entry (or m1 {}) (seq m2)))]
	//	      (reduce merge2 maps))))
	public static IPersistentMap merge_with(final IFn f, final IPersistentMap... maps) {
		if (some(identity, maps) != null) {
			final IFn merge_entry = new AFn() {
				@Override
				public Object invoke(Object map, Object e) throws Exception {
					IPersistentMap m = (IPersistentMap) map;
					Object k = ((MapEntry) e).key();
					Object v = ((MapEntry) e).val();
					if (m.containsKey(k))
						return assoc(m, k, f.invoke(m.valAt(k), v));
					return assoc(m, k, v);
				}
			};
			IFn merge2 = new AFn() {
				public Object invoke(Object m1, Object m2) throws Exception {
					return reduce(merge_entry, (m1 != null ? m1 : RT.map()), seq(m2));
				};
			};
			return reduce(merge2, maps);
		}
		return null;
	}

	//	(defn some
	//	  "Returns the first logical true value of (pred x) for any x in coll,
	//	  else nil.  One common idiom is to use a set as pred, for example
	//	  this will return :fred if :fred is in the sequence, otherwise nil:
	//	  (some #{:fred} coll)"
	//	  {:added "1.0"}
	//	  [pred coll]
	//	    (when (seq coll)
	//	      (or (pred (first coll)) (recur pred (next coll)))))
	public static Object some(IFn pred, Object coll) {
		try {
			if (seq(coll) != null) {
				Object result = pred.invoke(first(coll));
				return result != null ? result : some(pred, next(coll));
			}
			return null;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//	(def
	//	 ^{:arglists '([coll x] [coll x & xs])
	//	   :doc "conj[oin]. Returns a new collection with the xs
	//	    'added'. (conj nil item) returns (item).  The 'addition' may
	//	    happen at different 'places' depending on the concrete type."
	//	   :added "1.0"}
	//	 conj (fn conj 
	//	        ([coll x] (. clojure.lang.RT (conj coll x)))
	//	        ([coll x & xs]
	//	         (if xs
	//	           (recur (conj coll x) (first xs) (next xs))
	//	           (conj coll x)))))
	public static IPersistentCollection conj(IPersistentCollection coll, Object x) {
		return RT.conj((IPersistentCollection) coll, x);
	}
	public static IPersistentCollection conj(IPersistentCollection coll, Object x, Object... xs) {
		ISeq s = seq(xs);
		if (s != null)
			return conj(conj(coll, x), s.first(), s.next());
		return RT.conj((IPersistentCollection) coll, x);
	}

	public static Keyword keyword(String name) {
		return Keyword.intern(name);
	}
	
	//	(defn every?
	//	  "Returns true if (pred x) is logical true for every x in coll, else
	//	  false."
	//	  {:tag Boolean
	//	   :added "1.0"}
	//	  [pred coll]
	//	  (cond
	//	   (nil? (seq coll)) true
	//	   (pred (first coll)) (recur pred (next coll))
	//	   :else false))
	public static boolean isEvery(IFn pred, Object coll) {
		try {
			ISeq seq = seq(coll);
			if (seq == null)
				return true;
			if ((Boolean) pred.invoke(seq.first()))
				return isEvery(pred, seq.next());
			return false;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//	(def
	//	    ^{:arglists '([f coll] [f val coll])
	//	      :doc "f should be a function of 2 arguments. If val is not supplied,
	//	  returns the result of applying f to the first 2 items in coll, then
	//	  applying f to that result and the 3rd item, etc. If coll contains no
	//	  items, f must accept no arguments as well, and reduce returns the
	//	  result of calling f with no arguments.  If coll has only 1 item, it
	//	  is returned and f is not called.  If val is supplied, returns the
	//	  result of applying f to val and the first item in coll, then
	//	  applying f to that result and the 2nd item, etc. If coll contains no
	//	  items, returns val and f is not called."
	//	      :added "1.0"}    
	//	    reduce
	//	     (fn r
	//	       ([f coll]
	//	             (let [s (seq coll)]
	//	               (if s
	//	                 (r f (first s) (next s))
	//	                 (f))))
	//	       ([f val coll]
	//	          (let [s (seq coll)]
	//	            (if s
	//	              (if (chunked-seq? s)
	//	                (recur f 
	//	                       (.reduce (chunk-first s) f val)
	//	                       (chunk-next s))
	//	                (recur f (f val (first s)) (next s)))
	//	              val)))))
    @SuppressWarnings("unchecked")
	public static <T> T reduce(IFn f, Object coll) {
    	try {
	    	ISeq s = seq(coll);
	    	if (s != null)
				return reduce(f, s.first(), s.next());
			return (T) f.invoke();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
    @SuppressWarnings("unchecked")
	public static <T> T reduce(IFn f, Object val, Object coll) {
    	try {
			ISeq s = seq(coll);
			if (s != null)
				return reduce(f, f.invoke(val, s.first()), s.next());
			return (T) val;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//  (defn range 
	//	  "Returns a lazy seq of nums from start (inclusive) to end
	//	  (exclusive), by step, where start defaults to 0, step to 1, and end
	//	  to infinity."
	//	  {:added "1.0"}
	//	  ([] (range 0 Double/POSITIVE_INFINITY 1))
	//	  ([end] (range 0 end 1))
	//	  ([start end] (range start end 1))
	//	  ([start end step]
	//	   (lazy-seq
	//	    (let [b (chunk-buffer 32)
	//	          comp (if (pos? step) < >)]
	//	      (loop [i start]
	//	        (if (and (< (count b) 32)
	//	                 (comp i end))
	//	          (do
	//	            (chunk-append b i)
	//	            (recur (+ i step)))
	//	          (chunk-cons (chunk b) 
	//	                      (when (comp i end) 
	//	                        (range i end step)))))))))
    public static ISeq range() {
    	return range(0, Double.POSITIVE_INFINITY, 1);
    }
    public static ISeq range(Number end) {
    	return range(0, end, 1);
    }
    public static ISeq range(Number start, Number end) {
    	return range(start, end, 1);
    }
    public static ISeq range(Number start, Number end, Number step) {
    	IPersistentCollection range = RT.vector();
    	for (Number i = start;;i = add(i, step)) {
    		if (isPos(step)) {
    			if (lt(i, end))
    				range = conj(range, i);
    			else
    				return seq(range);
    		} else {
    			if (gt(i, end))
    				range = conj(range, i);
    			else
    				return seq(range);
    		}
    	}
    }

	//    (defn sort
	//	  "Returns a sorted sequence of the items in coll. If no comparator is
	//	  supplied, uses compare. comparator must
	//	  implement java.util.Comparator."
	//	  {:added "1.0"}
	//	  ([coll]
	//	   (sort compare coll))
	//	  ([^java.util.Comparator comp coll]
	//	   (if (seq coll)
	//	     (let [a (to-array coll)]
	//	       (. java.util.Arrays (sort a comp))
	//	       (seq a))
	//	     ())))
    public static ISeq sort(Object coll) {
    	return sort(compare, coll);
    }
    public static ISeq sort(Comparator<Object> comp, Object coll) {
    	try {
	    	if (seq(coll) != null) {
	    		Object[] a = RT.toArray(coll);
	    		Arrays.sort(a, comp);
	    		return seq(a);
	    	} else
	    		return list();
    	}
    	catch (Exception e) {
    		throw new RuntimeException(e);
    	}
    }

	//  (defn sort-by
	//	  "Returns a sorted sequence of the items in coll, where the sort
	//	  order is determined by comparing (keyfn item).  If no comparator is
	//	  supplied, uses compare. comparator must
	//	  implement java.util.Comparator."
	//	  {:added "1.0"}
	//	  ([keyfn coll]
	//	   (sort-by keyfn compare coll))
	//	  ([keyfn ^java.util.Comparator comp coll]
	//	   (sort (fn [x y] (. comp (compare (keyfn x) (keyfn y)))) coll)))
    public static ISeq sort_by(IFn keyfn, Object coll) {
    	return sort_by(keyfn, compare, coll);
    }
	public static ISeq sort_by(final IFn keyfn, final Comparator<Object> comp, Object coll) {
		return sort(new Comparator<Object>() {
			public int compare(Object x, Object y) {
				try {
					return comp.compare(keyfn.invoke(x), keyfn.invoke(y));
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}, coll);
	}

	//  (defn partition
	//	  "Returns a lazy sequence of lists of n items each, at offsets step
	//	  apart. If step is not supplied, defaults to n, i.e. the partitions
	//	  do not overlap. If a pad collection is supplied, use its elements as
	//	  necessary to complete last partition upto n items. In case there are
	//	  not enough padding elements, return a partition with less than n items."
	//	  {:added "1.0"}
	//	  ([n coll]
	//	     (partition n n coll))
	//	  ([n step coll]
	//	     (lazy-seq
	//	       (when-let [s (seq coll)]
	//	         (let [p (take n s)]
	//	           (when (= n (count p))
	//	             (cons p (partition n step (drop step s))))))))
	//	  ([n step pad coll]
	//	     (lazy-seq
	//	       (when-let [s (seq coll)]
	//	         (let [p (take n s)]
	//	           (if (= n (count p))
	//	             (cons p (partition n step pad (drop step s)))
	//	             (list (take n (concat p pad)))))))))
    public static ISeq partition(int n, Object coll) {
    	return partition(n, n, coll);
    }
    public static ISeq partition(int n, int step, Object coll) {
    	ISeq s = seq(coll);
    	if (s != null) {
			ISeq p = take(n, s);
			if (equiv(n, count(p)))
				return cons(p, partition(n, step, drop(step, s)));
		}
    	return null;
    }
    public static ISeq partition(int n, int step, Object pad, Object coll) {
    	ISeq s = seq(coll);
    	if (s != null) {
			ISeq p = take(n, s);
			if (equiv(n, count(p)))
				return cons(p, partition(n, step, drop(step, s)));
		}
    	return null;
    }

	//  (defn drop
	//	  "Returns a lazy sequence of all but the first n items in coll."
	//	  {:added "1.0"}
	//	  [n coll]
	//	  (let [step (fn [n coll]
	//	               (let [s (seq coll)]
	//	                 (if (and (pos? n) s)
	//	                   (recur (dec n) (rest s))
	//	                   s)))]
	//	    (lazy-seq (step n coll))))
    public static ISeq drop(int n, Object coll) {
    	return dropStep(n, coll);
    }
    private static ISeq dropStep(int n, Object coll) {
    	ISeq s = seq(coll);
    	if (isPos(n) && s != null)
			return dropStep(dec(n), s.next());
    	return s;
    }
    
	//  (defn take
	//	  "Returns a lazy sequence of the first n items in coll, or all items if
	//	  there are fewer than n."
	//	  {:added "1.0"}
	//	  [n coll]
	//	  (lazy-seq
	//	   (when (pos? n) 
	//	     (when-let [s (seq coll)]
	//	      (cons (first s) (take (dec n) (rest s)))))))
    public static ISeq take(int n, Object coll) {
    	if (isPos(n)) {
	    	ISeq s = seq(coll);
	    	if (s != null)
				return RT.cons(s.first(), take(dec(n), s.next()));
    	}
    	return null;
    }

	//  (defn min
	//	  "Returns the least of the nums."
	//	  {:added "1.0"}
	//	  ([x] x)
	//	  ([x y] (if (< x y) x y))
	//	  ([x y & more]
	//	   (reduce min (min x y) more)))
    public static Object min(Object x) {
    	return x;
    }
    public static Object min(Object x, Object y) {
    	return lt(x, y) ? x : y;
    }
    public static Object min(Object x, Object y, Object... more) throws Exception {
    	IFn min = new AFn() {
    		@Override
    		public Object invoke(Object x, Object y) throws Exception {
    			return min(x, y);
    		}
    	};
		return reduce(min, min(x, y), more);
    }

	//  (defn max
	//	  "Returns the greatest of the nums."
	//	  {:added "1.0"}
	//	  ([x] x)
	//	  ([x y] (if (> x y) x y))
	//	  ([x y & more]
	//	   (reduce max (max x y) more)))
    public static Object max(Object x) {
    	return x;
    }
    public static Object max(Object x, Object y) {
    	return gt(x, y) ? x : y;
    }
    public static Object max(Object x, Object y, Object... more) throws Exception {
    	IFn max = new AFn() {
    		@Override
    		public Object invoke(Object x, Object y) throws Exception {
    			return max(x, y);
    		}
    	};
		return reduce(max, max(x, y), more);
    }

	//  (defn apply
	//	  "Applies fn f to the argument list formed by prepending args to argseq."
	//	  {:arglists '([f args* argseq])
	//	   :added "1.0"}
	//	  ([^clojure.lang.IFn f args]
	//	     (. f (applyTo (seq args))))
	//	  ([^clojure.lang.IFn f x args]
	//	     (. f (applyTo (list* x args))))
	//	  ([^clojure.lang.IFn f x y args]
	//	     (. f (applyTo (list* x y args))))
	//	  ([^clojure.lang.IFn f x y z args]
	//	     (. f (applyTo (list* x y z args))))
	//	  ([^clojure.lang.IFn f a b c d & args]
	//	     (. f (applyTo (cons a (cons b (cons c (cons d (spread args)))))))))
    public static Object apply(IFn f, Object args) {
    	try {
			return f.applyTo(seq(args));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
    }
    public static Object apply(IFn f, Object x, Object args) {
    	try {
	    	return f.applyTo(listStar(x, args));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
    }
    public static Object apply(IFn f, Object x, Object y, Object args) {
    	try {
	    	return f.applyTo(listStar(x, y, args));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
    }
    public static Object apply(IFn f, Object x, Object y, Object z, Object args) {
    	try {
			return f.applyTo(listStar(x, y, z, args));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
    }
    public static Object apply(IFn f, Object a, Object b, Object c, Object d, Object... args) {
    	try {
	    	return f.applyTo(listStar(cons(a, cons(b, cons(c, cons(d, spread(args)))))));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
    }
    
	//  (defn vec
	//    "Creates a new vector containing the contents of coll."
	//    {:added "1.0"}
	//    ([coll]
	//      (if (instance? java.util.Collection coll)
	//          (clojure.lang.LazilyPersistentVector/create coll)
	//          (. clojure.lang.LazilyPersistentVector (createOwning (to-array coll))))))
    @SuppressWarnings("rawtypes")
	public static IPersistentVector vec(Object coll) {
    	try {
	    	if (coll instanceof java.util.Collection)
				return LazilyPersistentVector.create((Collection) coll);
			return LazilyPersistentVector.createOwning(RT.toArray(coll));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

	//  (defn rand-int
	//	  "Returns a random integer between 0 (inclusive) and n (exclusive)."
	//	  {:added "1.0"}
	//	  [n] (int (rand n)))
    public static int rand_int(int n) {
    	return (int) (n * Math.random());
    }

	//  (defmacro sync
	//	  "transaction-flags => TBD, pass nil for now
	//
	//	  Runs the exprs (in an implicit do) in a transaction that encompasses
	//	  exprs and any nested calls.  Starts a transaction if none is already
	//	  running on this thread. Any uncaught exception will abort the
	//	  transaction and flow out of sync. The exprs may be run more than
	//	  once, but any effects on Refs will be atomic."
	//	  {:added "1.0"}
	//	  [flags-ignored-for-now & body]
	//	  `(. clojure.lang.LockingTransaction
	//	      (runInTransaction (fn [] ~@body))))
    @SuppressWarnings("unchecked")
	public static <T> T dosync(IFn f) {
		try {
			return (T) runInTransaction(f);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

	//  (defn send
	//	  "Dispatch an action to an agent. Returns the agent immediately.
	//	  Subsequently, in a thread from a thread pool, the state of the agent
	//	  will be set to the value of:
	//
	//	  (apply action-fn state-of-agent args)"
	//	  {:added "1.0"}
	//	  [^clojure.lang.Agent a f & args]
	//	    (. a (dispatch f args false)))
    public static Agent send(Agent a, IFn f, ISeq args) {
    	return (Agent) a.dispatch(f, args, false);
    }
    
	//	(defn send-off
	//	  "Dispatch a potentially blocking action to an agent. Returns the
	//	  agent immediately. Subsequently, in a separate thread, the state of
	//	  the agent will be set to the value of:
	//
	//	  (apply action-fn state-of-agent args)"
	//	  {:added "1.0"}
	//	  [^clojure.lang.Agent a f & args]
	//	    (. a (dispatch f args true)))
    public static Agent send_off(Agent a, IFn f, ISeq args) {
    	return (Agent) a.dispatch(f, args, true);
    }

	//  (defn list*
	//	  "Creates a new list containing the items prepended to the rest, the
	//	  last of which will be treated as a sequence."
	//	  {:added "1.0"}
	//	  ([args] (seq args))
	//	  ([a args] (cons a args))
	//	  ([a b args] (cons a (cons b args)))
	//	  ([a b c args] (cons a (cons b (cons c args))))
	//	  ([a b c d & more]
	//	     (cons a (cons b (cons c (cons d (spread more)))))))
    public static ISeq listStar(Object args) {
    	return seq(args);
    }
    public static ISeq listStar(Object a, Object args) {
    	return cons(a, args);
    }
    public static ISeq listStar(Object a, Object b, Object args) {
    	return cons(a, cons(b, args));
    }
    public static ISeq listStar(Object a, Object b, Object c, Object args) {
    	return cons(a, cons(b, cons(c, args)));
    }
    public static ISeq listStar(Object a, Object b, Object c, Object d, Object... more) {
    	return cons(a, cons(b, cons(c, cons(d, spread(more)))));
    }
    
	//  (defn spread
	//	  {:private true}
	//	  [arglist]
	//	  (cond
	//	   (nil? arglist) nil
	//	   (nil? (next arglist)) (seq (first arglist))
	//	   :else (cons (first arglist) (spread (next arglist)))))
    private static ISeq spread(Object arglist) {
    	ISeq s = seq(arglist);
    	if (s == null)
    		return null;
    	if (s.next() == null)
    		return seq(s.first());
    	return cons(s.first(), spread(s.next()));
    }

	public static final AFn addTwo = new AFn() {
		@Override
		public Object invoke(Object arg1, Object arg2) throws Exception {
			return add(arg1, arg2);
		}
	};
	public static final AFn assocTwo = new AFn() {
		@Override
		public Object invoke(Object m, Object key1, Object val1, Object key2, Object val2) throws Exception {
			m = assoc(m, key1, val1);
			return assoc(m, key2, val2);
		}
	};
	public static final AFn assocSingle = new AFn() {
		@Override
		public Object invoke(Object m, Object key, Object val) throws Exception {
			return assoc(m, key, val);
		}
	};
	public static final AFn dissocSingle = new AFn() {
		@Override
		public Object invoke(Object m, Object key) throws Exception {
			return dissoc(m, key);
		}
	};
}

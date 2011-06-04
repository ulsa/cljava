package com.jayway.cljava;

import static clojure.lang.Numbers.add;
import static clojure.lang.Numbers.dec;
import static clojure.lang.Numbers.divide;
import static clojure.lang.Numbers.gte;
import static clojure.lang.Numbers.inc;
import static clojure.lang.Numbers.isNeg;
import static clojure.lang.Numbers.isPos;
import static clojure.lang.Numbers.multiply;
import static clojure.lang.Numbers.remainder;
import static clojure.lang.PersistentStructMap.construct;
import static clojure.lang.PersistentStructMap.createSlotMap;
import static clojure.lang.RT.assoc;
import static clojure.lang.RT.conj;
import static clojure.lang.RT.count;
import static clojure.lang.RT.dissoc;
import static clojure.lang.RT.doubleCast;
import static clojure.lang.RT.first;
import static clojure.lang.RT.floatCast;
import static clojure.lang.RT.fourth;
import static clojure.lang.RT.intCast;
import static clojure.lang.RT.list;
import static clojure.lang.RT.nth;
import static clojure.lang.RT.second;
import static clojure.lang.RT.seq;
import static clojure.lang.RT.third;
import static clojure.lang.RT.vector;
import static com.jayway.cljava.Core.agent;
import static com.jayway.cljava.Core.dosync;
import static com.jayway.cljava.Core.keyword;
import static com.jayway.cljava.Core.map;
import static com.jayway.cljava.Core.merge_with;
import static com.jayway.cljava.Core.min;
import static com.jayway.cljava.Core.rand_int;
import static com.jayway.cljava.Core.reduce;
import static com.jayway.cljava.Core.send_off;
import static com.jayway.cljava.Core.sort_by;
import static com.jayway.cljava.Core.vec;

import java.util.Iterator;

import clojure.lang.AFn;
import clojure.lang.APersistentMap;
import clojure.lang.Agent;
import clojure.lang.Associative;
import clojure.lang.IFn;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import clojure.lang.PersistentStructMap;
import clojure.lang.PersistentStructMap.Def;
import clojure.lang.RT;
import clojure.lang.Range;
import clojure.lang.Ref;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.JFrame;

/**
 * Rich Hickey's Clojure ant colony simulation implemented in Java, using
 * Clojure's Java APIs for persistent data structures and concurrency
 * primitives.
 * 
 * @author Ulrik Sandberg
 */
public class Ants {

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

	// dimensions of square world
	private static final int dim = 80;
	// number of ants = nants-sqrt^2
	private static final int nants_sqrt = 7;
	// number of places with food
	private static final int food_places = 35;
	// range of amount of food at a place
	static final int food_range = 100;
	// scale factor for pheromone drawing
	private static final double pher_scale = 20.0;
	// scale factor for food drawing
	private static final double food_scale = 30.0;
	// evaporation rate
	private static final double evap_rate = 0.99;
	
	private static final int animation_sleep_ms = 100;
	private static final int ant_sleep_ms = 40;
	private static final int evap_sleep_ms = 1000;

	private static boolean running = true;

	// cell may also have :ant and :home
	private static Def cell = createSlotMap(list(keyword("food"), keyword("pher")));
	// ant may also have :food
	private static Def ant = createSlotMap(list(keyword("dir")));

	// world is a 2d vector of refs to cells
	private static IPersistentVector world;

	public static JPanel panel;
	public static JFrame frame;

	private static final int home_off = dim / 4;
	private static final Range home_range = new Range(home_off, nants_sqrt + home_off);

	public static void main(String[] args) throws Exception {
		//	;world is a 2d vector of refs to cells
		//	(def world 
		//	     (apply vector 
		//	            (map (fn [_] 
		//	                   (apply vector (map (fn [_] (ref (struct cell 0 0))) 
		//	                                      (range dim)))) 
		//	                 (range dim))))
		world = vec(map(new AFn() {
			@Override
			public Object invoke(Object _) throws Exception {
				return vec(map(new AFn() {
					@Override
					public Object invoke(Object _) throws Exception {
						return new Ref(construct(cell, list(0, 0)));
					}
				}, new Range(0, dim)));
			};
		}, new Range(0, dim)));

		// we do this here, since JPanel constructor calls render, which requires a world
		panel = panel();
		frame = frame(panel);
		
		// run
		
		// (def ants (setup))
		ISeq ants = setup();
		// (send-off animator animation)
		send_off(animator, new AFn() {
			@Override
			public Object invoke(Object x) throws Exception {
				return animation(x);
			}
		}, null);

		// (dorun (map #(send-off % behave) ants))
		map(new AFn() {
			@Override
			public Object invoke(Object arg1) throws Exception {
				Agent ant = (Agent) arg1;
				return send_off(ant, new AFn() {
					@Override
					public Object invoke(Object arg1) throws Exception {
						IPersistentVector loc = (IPersistentVector) arg1;
						return behave(loc);
					}
				}, null);
			}
		}, ants);
		
		// (send-off evaporator evaporation)
		send_off(evaporator, new AFn() {
			@Override
			public Object invoke(Object x) throws Exception {
				return evaporation(x);
			}
		}, null);
	}

	//	(defn place [[x y]]
	//	             (-> world (nth x) (nth y)))
	public static Ref place(IPersistentVector coords) {
		int x = (Integer) first(coords);
		int y = (Integer) second(coords);
		return (Ref) nth(nth(world, x), y);
	}
	
	//	(defn create-ant 
	//	  "create an ant at the location, returning an ant agent on the location"
	//	  [loc dir]
	//	    (dosync
	//	      (let [p (place loc)
	//	            a (struct ant dir)]
	//	        (alter p assoc :ant a)
	//	        (agent loc))))
	public static Agent create_ant(final IPersistentVector loc, final int dir) {
		return dosync(new AFn() {
				@Override
				public Object invoke() throws Exception {
					Ref p = place(loc);
					PersistentStructMap a = construct(ant, list(dir));
					p.alter(assocSingle, list(keyword("ant"), a));
					return new Agent(loc);
				}
			});
	}
	
	//	(defn setup 
	//	  "places initial food and ants, returns seq of ant agents"
	//	  []
	//	  (dosync
	//	    (dotimes [i food-places]
	//	      (let [p (place [(rand-int dim) (rand-int dim)])]
	//	        (alter p assoc :food (rand-int food-range))))
	//	    (doall
	//	     (for [x home-range y home-range]
	//	       (do
	//	         (alter (place [x y]) 
	//	                assoc :home true)
	//	         (create-ant [x y] (rand-int 8)))))))
	public static ISeq setup() {
		return dosync(new AFn() {
			@Override
			public Object invoke() throws Exception {
				for (int i = 0; i < food_places; i++) {
					Ref p = place(vector(rand_int(dim), rand_int(dim)));
					p.alter(assocSingle, list(keyword("food"), rand_int(food_range)));
				}
				IPersistentCollection ants = list();
				for (@SuppressWarnings("unchecked")
				Iterator<Integer> xIter = home_range.iterator(); xIter.hasNext();) {
					int x = xIter.next();
					for (@SuppressWarnings("unchecked")
					Iterator<Integer> yIter = home_range.iterator(); yIter.hasNext();) {
						int y = yIter.next();
						Ref p = place(vector(x, y));
						p.alter(assocSingle, list(keyword("home"), true));
						ants = conj(ants, create_ant(vector(x, y), rand_int(8)));
					}
				}
				return ants;
			}
		});
	}

	//	(defn bound 
	//	  "returns n wrapped into range 0-b"
	//	  [b n]
	//	    (let [n (rem n b)]
	//	      (if (neg? n) 
	//	        (+ n b) 
	//	        n)))
	public static Number bound(Number b, Number n) {
		Number n2 = remainder(n, b);
		if (isNeg(n2))
			return add(n2, b);
		return n2;
	}
	
	//	(defn wrand 
	//	  "given a vector of slice sizes, returns the index of a slice given a
	//	  random spin of a roulette wheel with compartments proportional to
	//	  slices."
	//	  [slices]
	//	  (let [total (reduce + slices)
	//	        r (rand total)]
	//	    (loop [i 0 sum 0]
	//	      (if (< r (+ (slices i) sum))
	//	        i
	//	        (recur (inc i) (+ (slices i) sum))))))
	public static int wrand(IPersistentVector slices) {
		int total = reduce(addTwo, slices);
		int r = rand_int(total);
		int i = 0;
		Number sum = 0;
		while (gte(r, add(slices.nth(i), sum))) {
			i = inc(i);
			sum = add(slices.nth(i), sum);
		}
		return i;
	}
	
	//	;dirs are 0-7, starting at north and going clockwise
	//	;these are the deltas in order to move one step in given dir
	//	(def dir-delta {0 [0 -1]
	//	                1 [1 -1]
	//	                2 [1 0]
	//	                3 [1 1]
	//	                4 [0 1]
	//	                5 [-1 1]
	//	                6 [-1 0]
	//	                7 [-1 -1]})
	public static final APersistentMap dir_delta = (APersistentMap) RT.map(
		0, vector(0, -1),
		1, vector(1, -1),
		2, vector(1, 0),
		3, vector(1, 1),
		4, vector(0, 1),
		5, vector(-1, 1),
		6, vector(-1, 0),
		7, vector(-1, -1));
	
	//	(defn delta-loc 
	//	  "returns the location one step in the given dir. Note the world is a torus"
	//	  [[x y] dir]
	//	    (let [[dx dy] (dir-delta (bound 8 dir))]
	//	      [(bound dim (+ x dx)) (bound dim (+ y dy))]))
	public static IPersistentVector delta_loc(IPersistentVector loc, Object dir) {
		try {
			int x = (Integer) first(loc);
			int y = (Integer) second(loc);
			IPersistentVector delta = (IPersistentVector) dir_delta.invoke(bound(8, (Number) dir));
			int dx = (Integer) first(delta);
			int dy = (Integer) second(delta);
			return vector(bound(dim, x + dx), bound(dim, y + dy));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//	;ant agent functions
	//	;an ant agent tracks the location of an ant, and controls the behavior of 
	//	;the ant at that location
	//
	//	(defn turn 
	//	  "turns the ant at the location by the given amount"
	//	  [loc amt]
	//	    (dosync
	//	     (let [p (place loc)
	//	           ant (:ant @p)]
	//	       (alter p assoc :ant (assoc ant :dir (bound 8 (+ (:dir ant) amt))))))
	//	    loc)
	public static IPersistentVector turn(final IPersistentVector loc, final int amt) {
		return dosync(new AFn() {
			@Override
			public Object invoke() throws Exception {
				Ref p = place(loc);
				Associative ant = derefGet(p, keyword("ant"));
				Integer oldDir = (Integer) ant.valAt(keyword("dir"));
				ant = assoc(ant, keyword("dir"), bound(8, oldDir + amt));
				p.alter(assocSingle, list(keyword("ant"), ant));
				return loc;
			}
		});
	}
	
	//	(defn move 
	//	  "moves the ant in the direction it is heading. Must be called in a
	//	  transaction that has verified the way is clear"
	//	  [loc]
	//	     (let [oldp (place loc)
	//	           ant (:ant @oldp)
	//	           newloc (delta-loc loc (:dir ant))
	//	           p (place newloc)]
	//	         ;move the ant
	//	       (alter p assoc :ant ant)
	//	       (alter oldp dissoc :ant)
	//	         ;leave pheromone trail
	//	       (when-not (:home @oldp)
	//	         (alter oldp assoc :pher (inc (:pher @oldp))))
	//	       newloc))
	public static IPersistentVector move(IPersistentVector loc) {
		try {
			Ref oldp = place(loc);
			IPersistentMap ant = derefGet(oldp, keyword("ant"));
			IPersistentVector newloc = delta_loc(loc, ant.valAt(keyword("dir")));
			Ref p = place(newloc);
			// move the ant
			p.alter(assocSingle, list(keyword("ant"), ant));
			oldp.alter(dissocSingle, list(keyword("ant")));
			// leave pheromone trail
			if (derefGet(oldp, keyword("home")) == null) {
				Number incementedPheromones = inc(derefGet(oldp, keyword("pher")));
				oldp.alter(assocSingle, list(keyword("pher"), incementedPheromones));
			}
			return newloc;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T derefGet(Ref oldp, Object key) {
		return (T) ((IPersistentMap) oldp.deref()).valAt(key);
	}

	//  (defn take-food [loc]
	//     "Takes one food from current location. Must be called in a
	//     transaction that has verified there is food available"
	//     (let [p (place loc)
	//           ant (:ant @p)]    
	//       (alter p assoc 
	//              :food (dec (:food @p))
	//              :ant (assoc ant :food true))
	//       loc))
	public static IPersistentVector take_food(IPersistentVector loc) {
		try {
			Ref p = place(loc);
			IPersistentMap ant = derefGet(p, keyword("ant"));
			Number decrementedFood = dec(derefGet(p, keyword("food")));
			Associative antHasFood = assoc(ant, keyword("food"), true);
			p.alter(assocTwo, list(keyword("food"), decrementedFood, keyword("ant"), antHasFood));
			return loc;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//	(defn drop-food [loc]
	//     "Drops food at current location. Must be called in a
	//     transaction that has verified the ant has food"
	//     (let [p (place loc)
	//           ant (:ant @p)]    
	//       (alter p assoc 
	//              :food (inc (:food @p))
	//              :ant (dissoc ant :food))
	//       loc))
	public static IPersistentVector drop_food(IPersistentVector loc) {
		try {
			Ref p = place(loc);
			Associative ant = derefGet(p, keyword("ant"));
			Number incrementedFood = inc(derefGet(p, keyword("food")));
			Object antHasNoFood = dissoc(ant, keyword("food"));
			p.alter(assocTwo, list(keyword("food"), incrementedFood, keyword("ant"), antHasNoFood));
			return loc;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//	(defn rank-by 
	//	  "returns a map of xs to their 1-based rank when sorted by keyfn"
	//	  [keyfn xs]
	//	  (let [sorted (sort-by (comp float keyfn) xs)]
	//	    (reduce (fn [ret i] (assoc ret (nth sorted i) (inc i)))
	//	            {} (range (count sorted)))))
	public static IPersistentMap rank_by(final IFn keyfn, ISeq xs) {
		final ISeq sorted = sort_by(new AFn() {
			@Override
			public Object invoke(Object p) throws Exception {
				return floatCast(keyfn.invoke(p));
			}
		}, xs);
		return reduce(new AFn() {
			@Override
			public Object invoke(Object ret, Object i) throws Exception {
				return assoc(ret, nth(sorted, (Integer) i), inc(i));
			}
		}, RT.map(), new Range(0, count(sorted)));
	}

	//	(defn behave 
	//	  "the main function for the ant agent"
	//	  [loc]
	//	  (let [p (place loc)
	//	        ant (:ant @p)
	//	        ahead (place (delta-loc loc (:dir ant)))
	//	        ahead-left (place (delta-loc loc (dec (:dir ant))))
	//	        ahead-right (place (delta-loc loc (inc (:dir ant))))
	//	        places [ahead ahead-left ahead-right]]
	//	    (Thread/sleep ant-sleep-ms)
	//	    (dosync
	//	     (when running
	//	       (send-off *agent* behave))
	//	     (if (:food ant)
	//	       ;going home
	//	       (cond 
	//	        (:home @p)                              
	//	          (-> loc drop-food (turn 4))
	//	        (and (:home @ahead) (not (:ant @ahead))) 
	//	          (move loc)
	//	        :else
	//	          (let [ranks (merge-with + 
	//	                        (rank-by (comp #(if (:home %) 1 0) deref) places)
	//	                        (rank-by (comp :pher deref) places))]
	//	          (([move #(turn % -1) #(turn % 1)]
	//	            (wrand [(if (:ant @ahead) 0 (ranks ahead)) 
	//	                    (ranks ahead-left) (ranks ahead-right)]))
	//	           loc)))
	//	       ;foraging
	//	       (cond 
	//	        (and (pos? (:food @p)) (not (:home @p))) 
	//	          (-> loc take-food (turn 4))
	//	        (and (pos? (:food @ahead)) (not (:home @ahead)) (not (:ant @ahead)))
	//	          (move loc)
	//	        :else
	//	          (let [ranks (merge-with + 
	//	                                  (rank-by (comp :food deref) places)
	//	                                  (rank-by (comp :pher deref) places))]
	//	          (([move #(turn % -1) #(turn % 1)]
	//	            (wrand [(if (:ant @ahead) 0 (ranks ahead)) 
	//	                    (ranks ahead-left) (ranks ahead-right)]))
	//			   loc)))))))
	public static IPersistentVector behave(final IPersistentVector loc) {
		final Ref p = place(loc);
		final Associative ant = derefGet(p, keyword("ant"));
		final Ref ahead = place(delta_loc(loc, ant.valAt(keyword("dir"))));
		final Ref ahead_left = place(delta_loc(loc, dec(ant.valAt(keyword("dir")))));
		final Ref ahead_right = place(delta_loc(loc, inc(ant.valAt(keyword("dir")))));
		final IPersistentVector places = vector(ahead, ahead_left, ahead_right);
		sleep(ant_sleep_ms);
		return dosync(new AFn() {
			@Override
			public Object invoke() throws Exception {
				if (running) {
					send_off((Agent) RT.AGENT.deref(), new AFn() {
						@Override
						public Object invoke(Object arg1) throws Exception {
							return behave((IPersistentVector) arg1);
						}
					}, null);
				}
				if (ant.valAt(keyword("food")) != null) {
					// going home
					if (isHome(p)) {
						return turn(drop_food(loc), 4);
					} else if (isHome(ahead) && !isAnt(ahead)) {
						return move(loc);
					}
					//	          (let [ranks (merge-with + 
					//	                        (rank-by (comp #(if (:home %) 1 0) deref) places)
					//	                        (rank-by (comp :pher deref) places))]
					IPersistentMap ranks = merge_with(addTwo, rank_by(new AFn() {
						public Object invoke(Object p) throws Exception {
							return isHome((Ref) p) ? 1 : 0;
						};
					}, seq(places)), rank_by(new AFn() {
						public Object invoke(Object p) throws Exception {
							return pherAt((Ref) p);
						};
					}, seq(places)));
					//	          (([move #(turn % -1) #(turn % 1)]
					//	            (wrand [(if (:ant @ahead) 0 (ranks ahead)) 
					//	                    (ranks ahead-left) (ranks ahead-right)]))
					//	           loc)))
					IPersistentVector moves = vector(new AFn() {
						public Object invoke(Object pos) throws Exception {
							return move((IPersistentVector) pos);
						};
					}, new AFn() {
						public Object invoke(Object pos) throws Exception {
							return turn((IPersistentVector) pos, -1);
						};
					}, new AFn() {
						public Object invoke(Object pos) throws Exception {
							return turn((IPersistentVector) pos, 1);
						};
					});
					IFn move = (IFn) moves.nth(wrand(vector(isAnt(ahead) ? 0 : ranks.valAt(ahead), ranks.valAt(ahead_left), ranks.valAt(ahead_right))));
					return move.invoke(loc);
				}
				else {
					// foraging
					if (isPos(foodAt(p)) && !isHome(p)) {
						return turn(take_food(loc), 4);
					} else if (isPos(foodAt(ahead)) && !isHome(ahead) && !isAnt(ahead)) {
						return move(loc);
					}
					//	          (let [ranks (merge-with + 
					//	                                  (rank-by (comp :food deref) places)
					//	                                  (rank-by (comp :pher deref) places))]
					IPersistentMap ranks = merge_with(addTwo, rank_by(new AFn() {
						public Object invoke(Object p) throws Exception {
							return foodAt((Ref) p);
						};
					}, seq(places)), rank_by(new AFn() {
						public Object invoke(Object p) throws Exception {
							return pherAt((Ref) p);
						};
					}, seq(places)));
					//	          (([move #(turn % -1) #(turn % 1)]
					//	            (wrand [(if (:ant @ahead) 0 (ranks ahead)) 
					//	                    (ranks ahead-left) (ranks ahead-right)]))
					//			           loc)))))))
					IPersistentVector moves = vector(new AFn() {
						public Object invoke(Object pos) throws Exception {
							return move((IPersistentVector) pos);
						};
					}, new AFn() {
						public Object invoke(Object pos) throws Exception {
							return turn((IPersistentVector) pos, -1);
						};
					}, new AFn() {
						public Object invoke(Object pos) throws Exception {
							return turn((IPersistentVector) pos, 1);
						};
					});
					IFn move = (IFn) moves.nth(wrand(vector(isAnt(ahead) ? 0 : ranks.valAt(ahead), ranks.valAt(ahead_left), ranks.valAt(ahead_right))));
					return move.invoke(loc);
				}
			}
		});
	}
	
	//	(defn evaporate 
	//	  "causes all the pheromones to evaporate a bit"
	//	  []
	//	  (dorun 
	//	   (for [x (range dim) y (range dim)]
	//	     (dosync 
	//	      (let [p (place [x y])]
	//	        (alter p assoc :pher (* evap-rate (:pher @p))))))))
	public static void evaporate() {
		for (@SuppressWarnings("unchecked")
		Iterator<Integer> xIter = new Range(0, dim).iterator(); xIter.hasNext();) {
			final int x = xIter.next();
			for (@SuppressWarnings("unchecked")
			Iterator<Integer> yIter = new Range(0, dim).iterator(); yIter.hasNext();) {
				final int y = yIter.next();
				dosync(new AFn() {
					@Override
					public Object invoke() throws Exception {
						Ref p = place(vector(x, y));
						return p.alter(assocSingle, list(keyword("pher"), evap_rate * pherAt(p)));
					}
				});
			}
		}
	}

	private static boolean isAnt(final Ref p) {
		return derefGet(p, keyword("ant")) != null;
	}
	
	private static boolean isHome(final Ref p) {
		return derefGet(p, keyword("home")) != null;
	}
	
	private static int foodAt(final Ref p) {
		return derefGet(p, keyword("food"));
	}
	
	private static double pherAt(final Ref p) {
		return doubleCast(derefGet(p, keyword("pher")));
	}

	private static void sleep(int ms) {
		try {
			Thread.sleep(ms);
		}
		catch (InterruptedException e) {
		}
	}

	// ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; UI ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

	// ;pixels per world cell
	private static final int scale = 5;
	
	//	(defn fill-cell [#^Graphics g x y c]
	//    (doto g
	//      (.setColor c)
	//      (.fillRect (* x scale) (* y scale) scale scale)))
	public static Graphics fill_cell(Graphics g, int x, int y, Color c) {
		g.setColor(c);
		g.fillRect(x * scale, y * scale, scale, scale);
		return g;
	}

	//	(defn render-ant [ant #^Graphics g x y]
	//      (let [[hx hy tx ty] ({0 [2 0 2 4] 
	//                            1 [4 0 0 4] 
	//                            2 [4 2 0 2] 
	//                            3 [4 4 0 0] 
	//                            4 [2 4 2 0] 
	//                            5 [0 4 4 0] 
	//                            6 [0 2 4 2] 
	//                            7 [0 0 4 4]}
	//                           (:dir ant))]
	//        (doto g
	//          (.setColor (if (:food ant) 
	//                      (Color. 255 0 0 255) 
	//                      (Color. 0 0 0 255)))
	//          (.drawLine (+ hx (* x scale)) (+ hy (* y scale)) 
	//                    (+ tx (* x scale)) (+ ty (* y scale))))))
	public static Graphics render_ant(IPersistentMap ant, Graphics g, int x, int y) {
		IPersistentMap dirs = RT.map(
				0, vector(2, 0, 2, 4),
				1, vector(4, 0, 0, 4),
				2, vector(4, 2, 0, 2),
				3, vector(4, 4, 0, 0),
				4, vector(2, 4, 2, 0),
				5, vector(0, 4, 4, 0),
				6, vector(0, 2, 4, 2),
				7, vector(0, 0, 4, 4));
		Integer i = (Integer) ant.valAt(keyword("dir"));
		IPersistentVector v = (IPersistentVector) dirs.valAt(i);
		int hx = (Integer) first(v);
		int hy = (Integer) second(v);
		int tx = (Integer) third(v);
		int ty = (Integer) fourth(v);
		g.setColor(ant.valAt(keyword("food")) != null ? new Color(255, 0, 0, 255) : new Color(0, 0, 0, 255));
		g.drawLine(hx + (x * scale), hy + (y * scale), tx + (x * scale), ty + (y * scale));
		return g;
	}
	
	//	(defn render-place [g p x y]
	//    (when (pos? (:pher p))
	//      (fill-cell g x y (Color. 0 255 0 
	//                            (int (min 255 (* 255 (/ (:pher p) pher-scale)))))))
	//    (when (pos? (:food p))
	//      (fill-cell g x y (Color. 255 0 0 
	//                            (int (min 255 (* 255 (/ (:food p) food-scale)))))))
	//    (when (:ant p)
	//      (render-ant (:ant p) g x y)))
	public static Graphics render_place(Graphics g, Object place, int x, int y) {
		IPersistentMap p = (IPersistentMap) place;
		Graphics newg = null;
		if (isPos(p.valAt(keyword("pher"))))
			newg = fill_cell(g, x, y,
					new Color(0, 255, 0, intCast(min(255, multiply(255, divide(p.valAt(keyword("pher")), pher_scale))))));
		if (isPos(p.valAt(keyword("food"))))
			newg = fill_cell(g, x, y,
					new Color(255, 0, 0, intCast(min(255, multiply(255, divide(p.valAt(keyword("food")), food_scale))))));
		if (p.valAt(keyword("ant")) != null)
			newg = render_ant((IPersistentMap) p.valAt(keyword("ant")), g, x, y);
		return newg;
	}

	//	(defn render [g]
	//      (let [v (dosync (apply vector (for [x (range dim) y (range dim)] 
	//                                       @(place [x y]))))
	//            img (BufferedImage. (* scale dim) (* scale dim) 
	//                (BufferedImage/TYPE_INT_ARGB))
	//            bg (.getGraphics img)]
	//        (doto bg
	//          (.setColor (Color/white))
	//          (.fillRect 0 0 (.getWidth img) (.getHeight img)))
	//        (dorun 
	//         (for [x (range dim) y (range dim)]
	//           (render-place bg (v (+ (* x dim) y)) x y)))
	//        (doto bg
	//          (.setColor (Color/blue))
	//          (.drawRect (* scale home-off) (* scale home-off) 
	//                     (* scale nants-sqrt) (* scale nants-sqrt)))
	//        (. g (drawImage img 0 0 nil))
	//        (. bg (dispose))))
	public static void render(Graphics g) {
		IPersistentVector v = dosync(new AFn() {
			@Override
			public Object invoke() throws Exception {
				IPersistentCollection places = vector();
				for (@SuppressWarnings("unchecked")
				Iterator<Integer> xIter = new Range(0, dim).iterator(); xIter.hasNext();) {
					int x = xIter.next();
					for (@SuppressWarnings("unchecked")
					Iterator<Integer> yIter = new Range(0, dim).iterator(); yIter.hasNext();) {
						int y = yIter.next();
						Ref p = place(vector(x, y));
						places = conj(places, p.deref());
					}
				}
				return places;
			}
		});
		BufferedImage img = new BufferedImage(scale * dim, scale * dim, BufferedImage.TYPE_INT_ARGB);
		Graphics bg = img.getGraphics();
		bg.setColor(Color.white);
		bg.fillRect(0, 0, img.getWidth(), img.getHeight());
		for (@SuppressWarnings("unchecked")
		Iterator<Integer> xIter = new Range(0, dim).iterator(); xIter.hasNext();) {
			int x = xIter.next();
			for (@SuppressWarnings("unchecked")
			Iterator<Integer> yIter = new Range(0, dim).iterator(); yIter.hasNext();) {
				int y = yIter.next();
				render_place(bg, v.nth(x*dim + y), x, y);
			}
		}
		bg.setColor(Color.blue);
		bg.drawRect(scale * home_off, scale * home_off, scale * nants_sqrt, scale * nants_sqrt);
		g.drawImage(img, 0, 0, null);
		bg.dispose();
	}

	//	(def panel (doto (proxy [JPanel] []
	//			                (paint [g] (render g)))
	//			     (.setPreferredSize (Dimension. (* scale dim) (* scale dim)))))
	@SuppressWarnings("serial")
	public static JPanel panel() {
		JPanel panel = new JPanel() {
			public void paint(Graphics g) {
				render(g);
			};
		};
		panel.setPreferredSize(new Dimension(scale * dim, scale * dim));
		return panel;
	}

	//	(def frame (doto (JFrame.) (.add panel) .pack .show))
	public static JFrame frame(JPanel panel) {
		JFrame frame = new JFrame();
		frame.add(panel);
		frame.pack();
		frame.setVisible(true);
		return frame;
	}

	//	(def animator (agent nil))
	public static final Agent animator = agent(null);

	//	(defn animation [x]
	//    (when running
	//      (send-off *agent* animation))
	//    (.repaint panel)
	//    (Thread/sleep animation-sleep-ms)
	//    nil)
	public static Object animation(final Object x) throws Exception {
		if (running) {
			send_off((Agent) RT.AGENT.deref(), new AFn() {
				@Override
				public Object invoke(Object arg1) throws Exception {
					return animation(arg1);
				}
			}, null);
		}
		panel.repaint();
		sleep(animation_sleep_ms);
		return null;
	}
	
	//   (def evaporator (agent nil))
	public static Agent evaporator = agent(null);
	
	//  (defn evaporation [x]
	//    (when running
	//      (send-off *agent* evaporation))
	//    (evaporate)
	//    (Thread/sleep evap-sleep-ms)
	//    nil)
	public static Object evaporation(final Object x) throws Exception {
		if (running) {
			send_off((Agent) RT.AGENT.deref(), new AFn() {
				@Override
				public Object invoke(Object arg1) throws Exception {
					return evaporation(arg1);
				}
			}, null);
		}
		evaporate();
		sleep(evap_sleep_ms);
		return null;
	}
}

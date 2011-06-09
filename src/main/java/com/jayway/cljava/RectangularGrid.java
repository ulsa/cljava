package com.jayway.cljava;

import static clojure.lang.RT.conj;
import static com.jayway.cljava.Core.addTwo;
import static com.jayway.cljava.Core.range;
import static com.jayway.cljava.Core.reduce;
import clojure.lang.AFn;
import clojure.lang.IFn;
import clojure.lang.IPersistentCollection;
import clojure.lang.RT;

public class RectangularGrid {
	//  (defn rect [w h]
	//    (let [diff (- w h)]
	//      (reduce + (for [i (range 1 (inc w))]
	//                  (- (reduce + (range i (* i (inc h)) i))
	//                     (if (< i diff) 0 (* i (- i diff))))))))
	public long countRectangles(int width, int height) {
		try {
			final int w = width;
			final int h = height;
			final int diff = w - h;
			return reduce(addTwo, new AFn() {
				@Override
				public Object invoke() throws Exception {
					IPersistentCollection rows = RT.vector();
					for (long i = 1; i <= w; i++) {
						long possibleSquare = i < diff ? 0 : i * (i - diff);
						long rectanglesOnRow = reduce(addTwo, range(i, i * (h + 1), i));
						rows = conj(rows, rectanglesOnRow - possibleSquare);
					}
					return rows;
				}
			}.invoke());
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) {
		final RectangularGrid rectangularGrid = new RectangularGrid();
		System.out.println("> countRectangles(3, 3)"); System.out.println(rectangularGrid.countRectangles(3, 3));
		System.out.println("> countRectangles(5, 2)"); System.out.println(rectangularGrid.countRectangles(5, 2));
		System.out.println("> countRectangles(10, 10)"); System.out.println(rectangularGrid.countRectangles(10, 10));
		System.out.println("> countRectangles(1, 1)"); System.out.println(rectangularGrid.countRectangles(1, 1));
		System.out.println("> countRectangles(592, 964)"); time(callCountRectangles(592, 964));
		System.out.println("> countRectangles(1000, 1000)"); time(callCountRectangles(1000, 1000));
		System.out.println("> countRectangles(2000, 2000)"); time(callCountRectangles(2000, 2000));
		System.out.println("> countRectangles(10000, 10000)"); time(callCountRectangles(10000, 10000));
	}
	
	static IFn callCountRectangles(final int width, final int height) {
		return new AFn() {
			@Override
			public Object invoke() throws Exception {
				return new RectangularGrid().countRectangles(width, height);
			}
		};
	}

	private static Object time(IFn f) {
		try {
			long start = System.nanoTime();
			Object result = f.invoke();
			System.out.println(String.format("Elapsed time: %.3f msecs", (System.nanoTime() - start)/1000000.0));
			System.out.println(result);
			return result;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}

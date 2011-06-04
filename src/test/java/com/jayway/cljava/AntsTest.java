package com.jayway.clojureds;


import static clojure.lang.RT.list;
import static clojure.lang.RT.vector;
import static com.jayway.clojureds.Ants.behave;
import static com.jayway.clojureds.Ants.evaporate;
import static com.jayway.clojureds.Ants.place;
import static com.jayway.clojureds.Ants.rank_by;
import static com.jayway.clojureds.Core.dosync;
import static com.jayway.clojureds.Core.keyword;
import static com.jayway.clojureds.Core.rand_int;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import clojure.lang.AFn;
import clojure.lang.Ref;

public class AntsTest {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void rank_byShouldRankCellWithPheromonesHigherThanWithout() throws Exception {
		// test rank_by
		// (rank-by (comp :pher deref) places))
		System.out.println(rank_by(new AFn() {
			@Override
			public Object invoke(Object p) throws Exception {
				return Ants.derefGet((Ref) p, keyword("pher"));
			}
		}, list(place(vector(0,0)), place(vector(79,79)))));
	}
	
	@Test
	public void evaporateShouldDecreaseOneToPoint99() throws Exception {
		// test evaporation
		System.out.println("test evaporation");
		System.out.println(place(vector(79,79)).deref());
		evaporate();
		System.out.println(place(vector(79,79)).deref());
	}

	@Test
	public void behaveShouldMakeTheRightChoice() throws Exception {
		
		// test behave
		System.out.println("test behave");
		System.out.println(place(vector(0,0)).deref());
		System.out.println(place(behave(vector(0,0))).deref());
		System.out.println(place(behave(vector(0,0))).deref());

	}
	
	@Test
	public void stuff() throws Exception {
		dosync(new AFn() {
			public Object invoke() throws Exception {
				Ref p = place(vector(0, 0));
				p.alter(Ants.assocSingle, list(keyword("food"), rand_int(Ants.food_range)));
				System.out.println(vector(0, 0) + " now has " + p.deref());
				Ants.create_ant(vector(79, 79), 3);
				System.out.println(Ants.move(vector(79, 79)));
				System.out.println(place(vector(0, 0)).deref());
				System.out.println(Ants.take_food(vector(0, 0)));
				System.out.println(place(vector(0, 0)).deref());
				System.out.println(Ants.drop_food(vector(0, 0)));
				System.out.println(place(vector(0, 0)).deref());
				return null;
			};
		});
	}
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.structr.api.search;

/**
 *
 */
public interface RangeQuery<T> extends QueryPredicate {

	T getRangeStart();
	T getRangeEnd();
}

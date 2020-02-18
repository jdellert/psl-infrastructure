package de.tuebingen.sfs.psl.util.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class SetOperations 
{
	public static <T> List<Set<T>> getSubsets(List<T> superSet) 
	{
	    List<Set<T>> res = new ArrayList<Set<T>>();
	    for (int k = 0; k <= superSet.size(); k++)
	    {
	    	getSubsets(superSet, k, 0, new TreeSet<T>(), res);
	    }
	    return res;
	}
	
	public static <T> List<Set<T>> getSubsets(List<T> superSet, int k) 
	{
	    List<Set<T>> res = new ArrayList<Set<T>>();
	    getSubsets(superSet, k, 0, new TreeSet<T>(), res);
	    return res;
	}
	
	private static <T> void getSubsets(List<T> superSet, int k, int idx, Set<T> current,List<Set<T>> solution) 
	{
	    //successful stop clause
	    if (current.size() == k) 
	    {
	        solution.add(new TreeSet<T>(current));
	        return;
	    }
	    
	    //unsuccessful stop clause
	    if (idx == superSet.size()) return;
	    T x = superSet.get(idx);
	    current.add(x);
	    //"guess" x is in the subset
	    getSubsets(superSet, k, idx+1, current, solution);
	    current.remove(x);
	    //"guess" x is not in the subset
	    getSubsets(superSet, k, idx+1, current, solution);
	}
	
	public static <T> Set<T> getIntersection(Set<T> set1, Set<T> set2) 
	{
	    boolean set1IsLarger = set1.size() > set2.size();
	    Set<T> cloneSet = new HashSet<T>(set1IsLarger ? set2 : set1);
	    cloneSet.retainAll(set1IsLarger ? set1 : set2);
	    return cloneSet;
	}
	
	public static <T> Set<T> getIntersection(T[] list1, T[] list2) 
	{
		Set<T> set1 = new HashSet<T>(Arrays.asList(list1)); 
		Set<T> set2 = new HashSet<T>(Arrays.asList(list2));
	    boolean set1IsLarger = set1.size() > set2.size();
	    Set<T> cloneSet = new HashSet<T>(set1IsLarger ? set2 : set1);
	    cloneSet.retainAll(set1IsLarger ? set1 : set2);
	    return cloneSet;
	}

	public static <T> Set<T> getUnion(Set<T> set1, Set<T> set2) 
	{
	    boolean set1IsLarger = set1.size() > set2.size();
	    Set<T> cloneSet = new HashSet<T>(set1IsLarger ? set1 : set2);
	    cloneSet.addAll(set1IsLarger ? set2 : set1);
	    return cloneSet;
	}
}

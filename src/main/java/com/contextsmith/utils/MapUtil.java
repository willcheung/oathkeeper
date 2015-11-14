package com.contextsmith.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class MapUtil {
  
//A generic Comparator that calls value.compareTo().
 private static class ByValue<K, V extends Comparable<V>>
 implements Comparator<Entry<K, V>> {
   @Override
   public int compare(Entry<K, V> o1, Entry<K, V> o2) {
     return o2.getValue().compareTo(o1.getValue());
   }
 }
 
 public static void sortByStringLength(List<String> input,
     final boolean ascending) {
   Collections.sort(input, new Comparator<String>() {
     @Override
     public int compare(String s1, String s2) {
       if (ascending) {
         return Integer.compare(s1.length(), s2.length());
       } else {
         return Integer.compare(s2.length(), s1.length());
       }
     }
   });
 }

 public static <K, V extends Comparable<V>> List<Entry<K, V>> sortByValue(
     Map<K, V> map) {
   List<Entry<K, V>> entries = new ArrayList<Entry<K, V>>(map.entrySet());
   Collections.sort(entries, new ByValue<K, V>());
   return entries;
 }

}

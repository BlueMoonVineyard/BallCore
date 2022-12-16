package BallCore.DataStructures

import java.util.Collections.synchronizedMap

import scala.collection.JavaConverters._
import scala.collection.mutable

class LRUCache[K, V](maxEntries: Int, eviction: (K, V) => Unit)
  extends java.util.LinkedHashMap[K, V](100, .75f, true) {

  override def removeEldestEntry(eldest: java.util.Map.Entry[K, V]): Boolean =
    if size > maxEntries then
      eviction(eldest.getKey(), eldest.getValue())
    size > maxEntries
}

object LRUCache {
  def apply[K, V](maxEntries: Int, eviction: (K, V) => Unit): mutable.Map[K, V] 
    = (new LRUCache[K, V](maxEntries, eviction)).asScala
}

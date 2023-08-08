// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.DataStructures

import scala.jdk.CollectionConverters._
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

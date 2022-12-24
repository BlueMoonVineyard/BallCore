// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.UI

import scala.xml.Elem
import scala.xml.Node
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority

// trait AccumulatorFn:

object Accumulator:
    def run(inner: (Accumulator) ?=> Unit): List[Node] =
        given akku: Accumulator = Accumulator()
        inner
        akku.items.toList

class Accumulator:
    var items = scala.collection.mutable.ArrayBuffer[Node]()
    def add(item: Node): Unit =
        items.append(item)

object Elements:
    val nil: (Accumulator) ?=> Unit = {

    }

    def Root(title: String, rows: Int)(inner: (Accumulator) ?=> Unit = nil): Elem =
        // val doot = paginatedPane
        <chestgui title={title} rows={rows.toString}>
            { Accumulator.run(inner) }
        </chestgui>

    def PaginatedPane(x: Int, y: Int, length: Int, height: Int)(inner: (Accumulator) ?=> Unit = nil)(using an: Accumulator): Unit =
        an add <paginatedpane x={x.toString} y={y.toString} length={length.toString} height={height.toString}>
            { Accumulator.run(inner) }
        </paginatedpane>

    def Page(inner: (Accumulator) ?=> Unit = nil)(using an: Accumulator): Unit =
        an add <page>
            { Accumulator.run(inner) }
        </page>

    def OutlinePane(x: Int, y: Int, length: Int, height: Int, priority: Priority = Priority.NORMAL, repeat: Boolean = false)(inner: (Accumulator) ?=> Unit = nil)(using an: Accumulator): Unit =
        an add <outlinepane x={x.toString} y={y.toString} length={length.toString} height={height.toString} priority={priority.toString().toLowerCase()} repeat={if repeat then "true" else "false"}>
            { Accumulator.run(inner) }
        </outlinepane>

    def StaticPane(x: Int, y: Int, length: Int, height: Int, priority: Priority = Priority.NORMAL, repeat: Boolean = false)(inner: (Accumulator) ?=> Unit = nil)(using an: Accumulator) =
        an add <staticpane x={x.toString} y={y.toString} length={length.toString} height={height.toString} priority={priority.toString().toLowerCase()} repeat={if repeat then "true" else "false"}>
            { Accumulator.run(inner) }
        </staticpane>

    def Item(id: String, onClick: String = "block", amount: Int = 1, displayName: Option[String] = None, lore: Option[List[String]] = None)(inner: (Accumulator) ?=> Unit = nil)(using an: Accumulator): Unit =
        an add <item id={id} amount={amount.toString} onClick={onClick}>
            { displayName.map(name => <displayname>{name}</displayname>) }
            { lore.map(lores => <lore>{lores.map (line => <line>{line}</line>)}</lore>) }
        </item>

    // def paginatedPane =
    //     <chestgui title="Shop" rows="6">
    //         <paginatedpane x="0" y="0" length="9" height="5">
    //             <page>
    //             <outlinepane x="0" y="0" length="9" height="5">
    //                 <item id="golden_sword" />
    //                 <item id="light_gray_glazed_terracotta" amount="16" />
    //                 <item id="cooked_cod" amount="64" />
    //             </outlinepane>
    //             </page>
    //         </paginatedpane>
    //         <outlinepane x="0" y="5" length="9" height="1" priority="lowest" repeat="true">
    //             <item id="black_stained_glass_pane" />
    //         </outlinepane>
    //         <staticpane x="0" y="5" length="9" height="1">
    //             <item id="red_wool" x="0" y="0" />
    //             <item id="barrier" x="4" y="0" />
    //             <item id="green_wool" x="8" y="0" />
    //         </staticpane>
    //     </chestgui>


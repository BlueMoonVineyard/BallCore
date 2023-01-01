// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.UI

import scala.xml.Elem
import scala.xml.Node
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority
import io.circe._, io.circe.parser._, io.circe.syntax._
import com.github.stefvanschie.inventoryframework.gui.`type`.util.Gui
import scala.reflect.ClassTag

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
    private val registeredProperties = scala.collection.mutable.Set[String]()
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

    def Item(id: String, onClick: ClickCallback = ClickCallback("block"), amount: Int = 1, displayName: Option[String] = None)(inner: (Accumulator) ?=> Unit = nil)(using an: Accumulator): Unit =
        val nodes = Accumulator.run(inner)
        val props = nodes.filter(_.label == "property")
        val lores = nodes.filter(_.label == "line")
        val other = nodes.filterNot(_.label == "property").filterNot(_.label == "line")
        an add <item id={id} amount={amount.toString} onClick={onClick.name}>
            { displayName.map(name => <displayname>{name}</displayname>) }
            { other }
            <lore>
                { lores }
            </lore>
            <properties>
                { props }
            </properties>
        </item>

    def Metadata[A](obj: A)(using an: Accumulator, enc: Encoder[A], dec: Decoder[A]): Unit =
        val name = obj.getClass.getCanonicalName
        if !registeredProperties.contains(name) then
            Gui.registerProperty(name, str => decode[A](str).toOption.get)
            registeredProperties.add(name)
        an add <property type={name}>{obj.asJson.noSpaces}</property>

    def Lore(line: String)(using an: Accumulator): Unit =
        an add <line>{line}</line>

    def SkullUsername(username: String)(using an: Accumulator): Unit =
        an add <skull owner={username} />

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


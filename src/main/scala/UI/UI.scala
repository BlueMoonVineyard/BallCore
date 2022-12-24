// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.UI

import com.github.stefvanschie.inventoryframework.gui.`type`.ChestGui
import scala.xml.Elem
import scala.xml.Node
import scala.xml.Text
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.entity.HumanEntity
import scala.xml.Atom

object UIHelpers:
    def toNode(n: Node, in: org.w3c.dom.Document): org.w3c.dom.Node =
        n match
            case Elem(prefix, label, attributes, scope, children @ _*) =>
                val r = in.createElementNS(prefix, label)
                for (a <- attributes) {
                    a.prefixedKey match
                        case s"$prefix:$key" =>
                            r.setAttributeNS(prefix, key, a.value.text)
                        case key =>
                            r.setAttribute(key, a.value.text)
                }
                for (c <- children) {
                    r.appendChild(toNode(c, in))
                }
                r
            case Text(text) =>
                in.createTextNode(text)
            case t: Atom[_] =>
                t.data match
                    case it: Some[_] =>
                        it.value match
                            case el: Elem =>
                                toNode(el, in)
                    case None =>
                        in.createComment("")
                    case _ =>
                        in.createCDATASection(t.data.toString())

    def newDocument(): org.w3c.dom.Document =
        val docFactory = DocumentBuilderFactory.newInstance();
        val docBuilder = docFactory.newDocumentBuilder();
        val doc = docBuilder.newDocument();
        doc

    def toW3C(e: Elem): org.w3c.dom.Element =
        val doc = newDocument()
        toNode(e, doc).asInstanceOf[org.w3c.dom.Element]

trait UI:
    var showingTo: HumanEntity = _

    def view(): Elem
    def update(): Unit =
        val newUI = ChestGui.load(this, UIHelpers.toW3C(view()))
        newUI.show(showingTo)
    def block(event: InventoryClickEvent): Unit =
        event.setCancelled(true)
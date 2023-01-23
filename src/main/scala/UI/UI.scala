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
import com.github.stefvanschie.inventoryframework.gui.`type`.util.Gui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.entity.HumanEntity
import scala.xml.Atom
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import org.bukkit.entity.Player
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.io.StringWriter

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

trait UITransferrer:
    def transferTo(p: UIProgram, f: p.Flags): Unit

trait UIPrompts:
    def prompt(prompt: String): Future[String]

trait UIServices extends UITransferrer, UIPrompts, ExecutionContext

trait UIProgram:
    given Conversion[Model, Future[Model]] = Future.successful(_)
    type Flags
    type Model
    type Message

    def init(flags: Flags): Model
    def view(model: Model): Elem
    def update(msg: Message, model: Model)(using services: UIServices): Future[Model]

class UIProgramRunner(program: UIProgram, flags: program.Flags, showingTo: Player)(using prompts: Prompts) extends UIServices:
    private var model = program.init(flags)
    private var transferred = false

    def render(): Unit =
        val mod = model
        Future {
            val res = program.view(mod)
            val dom = UIHelpers.toW3C(res)
            val newUI = ChestGui.load(this, dom)
            newUI.show(showingTo)
        }
    def block(event: InventoryClickEvent): Unit =
        event.setCancelled(true)
    def dispatch(event: InventoryClickEvent, obj: Object): Unit =
        implicit val s: UIServices = this
        event.setCancelled(true)
        program.update(obj.asInstanceOf[program.Message], model).map { x =>
            model = x
            if !transferred then
                render()
        }
    def transferTo(newProgram: UIProgram, newFlags: newProgram.Flags): Unit =
        transferred = true
        val newUI = UIProgramRunner(newProgram, newFlags, showingTo)
        newUI.render()
    def prompt(prompt: String): Future[String] =
        prompts.prompt(showingTo, prompt)
    def execute(runnable: Runnable): Unit =
        prompts.execute(runnable)
    def reportFailure(cause: Throwable): Unit =
        prompts.reportFailure(cause)

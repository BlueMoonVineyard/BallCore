// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.UI

import org.bukkit.event.inventory.InventoryClickEvent

import scala.annotation.tailrec
import scala.quoted.*

case class ClickCallback(name: String)

given ToExpr[ClickCallback] with
  def apply(x: ClickCallback)(using Quotes): Expr[ClickCallback] =
    '{ ClickCallback(${ Expr(x.name) }) }

private def inspectCode(x: Expr[Any])(using Quotes): Expr[ClickCallback] =

  @tailrec
  def extract(tree: Tree): String =
    tree match
      case Select(_, name) => name
      case Block(List(stmt), term) => extract(stmt)
      case DefDef(_, _, _, Some(term)) => extract(term)
      case Apply(term, _) => extract(term)
      case Inlined(_, _, term) => extract(term)
      case _ => throw new MatchError(s"unhandled ${x.asTerm}")

  val cb = ClickCallback(extract(x.asTerm))
  Expr(cb)

//noinspection Annotator
inline def callback(inline x: InventoryClickEvent => Unit): ClickCallback = ${
  inspectCode('x)
}
//noinspection Annotator
inline def callback[A](
                        inline x: (InventoryClickEvent, A) => Unit
                      ): ClickCallback = ${ inspectCode('x) }

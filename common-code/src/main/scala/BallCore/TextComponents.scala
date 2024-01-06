package BallCore

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.format.{Style, TextColor, TextDecoration}
import net.kyori.adventure.text.{Component, ComponentLike, TranslatableComponent}
import net.kyori.adventure.translation.Translatable

class TextComponents:
    extension (sc: StringContext)
        def txt(args: Any*): Component =
            val strings = sc.parts.iterator
            var it = Component.text(strings.next())
            val expressions = args.iterator
            while strings.hasNext do
                expressions.next() match
                    case c: ComponentLike => it = it.append(c)
                    case obj => it = it.append(Component.text(obj.toString))
                it = it.append(Component.text(strings.next()))
            it
        def trans(keys: String*): TranslatableComponent =
            val strings = sc.parts.iterator
            var it = strings.next()
            val expressions = keys.iterator
            while strings.hasNext do
                it += s".${expressions.next()}.${strings.next()}"
            Component.translatable(it)

    extension (a: Audience)
        def sendServerMessage(txt: Component): Unit =
            a.sendMessage(txt.color(Colors.serverMessage))

    extension (c: Component)
        def style(color: TextColor, decorations: TextDecoration*): Component =
            c.style(Style.style(color, decorations: _*))
        def not(decoration: TextDecoration): Component =
            c.decoration(decoration, false)

    extension (s: Seq[Component])
        def mkComponent(separator: Component): Component =
            var builder = Component.text()
            s.zipWithIndex.foreach { (component, idx) =>
                builder = builder.append(component)
                if idx != s.length - 1 then builder = builder.append(separator)
            }
            builder.asComponent()

    extension (o: Any)
        def toComponent: Component =
            Component.text(o.toString)

    extension (s: String)
        def toComponent: Component =
            Component.text(s)

    extension (t: Translatable)
        def asComponent: Component =
            Component.translatable(t)

    object Colors:
        val red: TextColor = TextColor.fromHexString("#e93d58")
        val teal: TextColor = TextColor.fromHexString("#00d485")
        val grellow: TextColor = TextColor.fromHexString("#b6e521")
        val yellow: TextColor = TextColor.fromHexString("#ffe247")
        val serverMessage: TextColor = TextColor.fromHexString("#6dd3ff")

    def txt(of: String): Component =
        Component.text(of)

    def keybind(of: String): Component =
        Component.keybind(of)

object TextComponents extends TextComponents

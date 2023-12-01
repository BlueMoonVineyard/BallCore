package BallCore.SpawnInventory

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import BallCore.TextComponents._
import scala.jdk.CollectionConverters._
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.format.StyleBuilderApplicable
import net.kyori.adventure.text.format.Style.Builder
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.event.ClickEvent

object Link extends TagResolver:
    private val urlColor: TextColor = TextColor.fromHexString("#2aa1bf")

    override def has(name: String): Boolean = name == "link"
    override def resolve(
        name: String,
        arguments: ArgumentQueue,
        ctx: Context,
    ): Tag =
        val page = arguments
            .popOr("Expected to find a page parameter: <number>")
            .lowerValue()
        Tag.styling(
            new StyleBuilderApplicable:
                override def styleApply(style: Builder): Unit =
                    val _ = style
                        .color(urlColor)
                        .clickEvent(ClickEvent.changePage(page))
        )

object Book:
    val builder = MiniMessage.builder().strict(true).build()

    val pages =
        List(
            """Welcome to <b><#da2117>Civ</#da2117><#93180f>Cubed</#93180f></b>!
              |
              |<b>Table of Contents</b>
              |- <link:2>Finding Somewhere
              |  To Settle</link>
              |- <link:6>Protecting Your Land</link>
              |- <link:9>Local Ores</link>
              |- <link:11>Local Plants</link>
              |- <link:13>Bulk Production</link>
              |- <link:17>What's Up With
              |  Furnaces?</link>
              |- <link:19>Commands</link>""".stripMargin,
            """<b>Finding Somewhere To Settle</b>
              |Different areas of CivCubed's world are primarily distinguished by what resources they have, and the two most important resources are Ores and Plants.
              |Ores vary by location in the world, while plants vary by climate.
            """.stripMargin,
            """You want to find somewhere that's unique in terms of location and climate, but you don't want to be too far from neighbours, as acquiring resources from far off lands is the basis of climbing up the tech tree.""".stripMargin,
            """You'll have a bonus for resources from where you settle and spend your time, and this bonus is critical for obtaining enough resources to climb up the tech tree and to maintain your position there.
              |In other words, you can't "one-man-band" the progression.""".stripMargin,
            """Note that oceans are barren of ores, and being close to x = 0 and z = 0 incurs a penalty for how many ores will be generated as you mine.""".stripMargin,
            """<b>Protecting Your Land</b>
              |When you spawn, you'll have a Civilization Heart.
              |These can be placed in order to "link" you to a certain location, which will accelerate the resource bonuses from staying somewhere for a long time.""".stripMargin,
            """But that's not all!
              |They can be bound to a group with the groups UI, which will unlock their ability to protect areas of land and restrict access to group members.
              |Right click the heart once you've done this.""".stripMargin,
            """After binding a Civilization Heart to a group, other players can place their hearts on yours in order to increase the area of land that it can cover.
              |These groups of one or more Civilization Hearts that protect land are called Civilization Beacons.""".stripMargin,
            """<b>Local Ores</b>
              |Unless you're settling in the southeast, you're not likely to see the vanilla ores that you're familiar with, but don't fret!
              |Many ores around the world are comparable to the familiar vanilla ores that you're familiar with.""".stripMargin,
            """They can make tools and armour with the same recipes as always.
              |Different ores of a "tier" yield similar but different tools.
              |Two of your local ores will be able to be combined into an alloy, which will last longer and work faster than the base ores.""".stripMargin,
            """<b>Local Plants</b>
              |Different plants from vanilla Minecraft are divided into different climates, and will grow during different seasons.
              |You can use /plants to look up a list of plants on the server, and see where and when they grow.""".stripMargin,
            """Breaking tall grass can yield seed items for all plants that can grow in an area.
              |
              |Wheat grows everywhere in all seasons.""".stripMargin,
            """<b>Bulk Production</b>
              |Climbing up the production tech tree in CivCubed primarily consists of workstations.
              |These workstations take three inputs: people, ingredients, and time.""".stripMargin,
            """The more people are working at a workstation, the faster the recipes will go.
              |Some recipes will require more than one person to work.""".stripMargin,
            """Recipes often vary by how many people are required: recipes with more people tend to yield more results compared to recipes with fewer people that yield less results.""".stripMargin,
            """To get started making workstations, put four crafting tables into 2x2 square in a crafting table, which will yield the Station Maker.
              |Place the Station Maker by a chest, and start making more stations!""".stripMargin,
            """<b>Furnaces</b>
              |Due to limitations of the server software being used, furnaces will spit out ores as items instead of depositing them into their internal slots—but don't fret!""".stripMargin,
            """Furnaces will deposit smelted ores into adjacent chests automatically.
              |Furnaces can be upgraded to yield more smelted ore per raw ore by surrounding them with specific ingots in a crafting table.""".stripMargin,
            """<b>Commands</b>
              |/groups: opens the groups UI
              |/plants: view the list of plants
              |/global: chat globally
              |/group <group>: chat in the given group
              |/local: chat locally""".stripMargin,
        ).map(x => builder.deserialize(x, Array[TagResolver](Link): _*))

    val book = net.kyori.adventure.inventory.Book
        .book(txt"Welcome to CivCubed!", txt"Janet", pages.asJava)

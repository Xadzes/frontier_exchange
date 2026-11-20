
Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/

## Armory enchanting economy

On the first launch, the server creates `config/frontier_exchange/enchantments.json`.
It contains per-level base costs, `COMMON`/`ADVANCED`/`RARE` multipliers, and
per-enchantment category and chapter rules. A transition price can contain
multiple item IDs. When upgrading an existing enchantment, only transitions
above its current level through the requested level are charged. Single-level
enchantments require a `fixed_price` entry.

The server validates item compatibility, conflicts, chapter access, maximum
level, and payment, then applies the enchantment and deducts resources as one
transaction. The UI only displays the quote returned by the server; it never
calculates the price or consumes experience.

The default fixed base prices are 8 iron ingots for Mending and 7 for Silk
Touch. Both use the `RARE` multiplier, so the default final prices are 16 and
14 iron ingots respectively.

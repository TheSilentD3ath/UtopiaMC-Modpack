package dev.utopia.core.character;

/**
 * Zugriff auf die Charakterdaten eines Spielers. Implementiert vom
 * PlayerEntityMixin - bewusst dieselbe Mixin-Klasse, die schon den
 * PlayerStatsManager traegt, damit keine zweite Injection noetig ist.
 */
public interface CharacterAccess {

    CharacterData utopia$getCharacter();
}

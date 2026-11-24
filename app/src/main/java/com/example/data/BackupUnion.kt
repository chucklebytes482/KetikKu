package com.example.data

import com.example.model.PersonalDictionaryEntry

/**
 * Gabungan per id: entri yang hanya ada di salah satu sisi selalu dipertahankan.
 * Jika ada di keduanya, versi dengan updatedAtMillis lebih baru menang (termasuk penanda hapus).
 * Seri: sisi [primary] menang.
 */
internal fun unionByIdNewestWins(
    primary: List<PersonalDictionaryEntry>,
    other: List<PersonalDictionaryEntry>
): List<PersonalDictionaryEntry> {
    val byId = LinkedHashMap<String, PersonalDictionaryEntry>()
    for (entry in other) byId[entry.id] = entry
    for (entry in primary) {
        val existing = byId[entry.id]
        if (existing == null || entry.updatedAtMillis >= existing.updatedAtMillis) byId[entry.id] = entry
    }
    return byId.values.toList()
}

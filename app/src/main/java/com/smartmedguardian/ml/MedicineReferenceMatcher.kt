package com.smartmedguardian.ml

import android.util.Log
import com.smartmedguardian.data.entity.MedicineReferenceImage
import com.smartmedguardian.data.entity.Prescription

object ReferenceMatcherConfig {
    /**
     * Minimum cosine similarity score required for a definitive MATCH.
     */
    const val MATCH_THRESHOLD = 0.72f

    /**
     * Minimum cosine similarity score required to declare an UNCERTAIN match.
     * Below this value, status is NO_MATCH.
     */
    const val UNCERTAIN_THRESHOLD = 0.55f
}

enum class MatchStatus {
    MATCH,       // High similarity >= MATCH_THRESHOLD
    UNCERTAIN,   // Similarity between UNCERTAIN_THRESHOLD and MATCH_THRESHOLD
    MISMATCH,    // Low similarity < UNCERTAIN_THRESHOLD or no reference photos
    NO_MATCH     // Alias for MISMATCH
}

data class ReferenceMatchResult(
    val prescription: Prescription?,
    val similarity: Float,
    val status: MatchStatus,
    val matchedImagePath: String? = null,
    val message: String
)

class MedicineReferenceMatcher {

    /**
     * Calculates cosine similarity between two float vectors.
     */
    fun calculateCosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0f

        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in vecA.indices) {
            dotProduct += vecA[i] * vecB[i]
            normA += vecA[i] * vecA[i]
            normB += vecB[i] * vecB[i]
        }

        if (normA <= 0f || normB <= 0f) return 0f
        val denom = (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())).toFloat()
        return if (denom > 0f) dotProduct / denom else 0f
    }

    /**
     * Matches a query embedding against all saved reference images associated with prescriptions.
     */
    fun findBestMatch(
        queryEmbedding: FloatArray,
        referenceImages: List<MedicineReferenceImage>,
        prescriptions: List<Prescription>,
        targetPrescriptionId: Long? = null
    ): ReferenceMatchResult {
        val filteredImages = if (targetPrescriptionId != null && targetPrescriptionId > 0L) {
            referenceImages.filter { it.prescriptionId == targetPrescriptionId }
        } else {
            referenceImages
        }

        if (filteredImages.isEmpty() || prescriptions.isEmpty()) {
            return ReferenceMatchResult(
                prescription = null,
                similarity = 0f,
                status = MatchStatus.MISMATCH,
                message = "No prescription reference photos available for comparison."
            )
        }

        val prescriptionMap = prescriptions.associateBy { it.id }

        var bestSimilarity = -1f
        var bestMatchImage: MedicineReferenceImage? = null
        var bestPrescription: Prescription? = null

        for (refImage in filteredImages) {
            val embeddingStr = refImage.embedding ?: continue
            val refEmbedding = try {
                MedicineEmbeddingHelper.stringToFloatArray(embeddingStr)
            } catch (e: Exception) {
                try {
                    Log.e("MedicineReferenceMatcher", "Failed to parse reference embedding for imageId=${refImage.id}", e)
                } catch (ignored: Throwable) {}
                continue
            }

            val sim = calculateCosineSimilarity(queryEmbedding, refEmbedding)
            try {
                Log.d("MedicineReferenceMatcher", "Similarity with prescriptionId=${refImage.prescriptionId}, imageId=${refImage.id}: $sim")
            } catch (ignored: Throwable) {}

            if (sim > bestSimilarity) {
                bestSimilarity = sim
                bestMatchImage = refImage
                bestPrescription = prescriptionMap[refImage.prescriptionId]
            }
        }

        try {
            Log.d("MedicineReferenceMatcher", "=== MATCH RESULT ===")
            Log.d("MedicineReferenceMatcher", "Best similarity: $bestSimilarity")
            Log.d("MedicineReferenceMatcher", "Matched Prescription: ${bestPrescription?.medicineName} (ID: ${bestPrescription?.id})")
            Log.d("MedicineReferenceMatcher", "=====================")
        } catch (ignored: Throwable) {}

        return when {
            bestSimilarity >= ReferenceMatcherConfig.MATCH_THRESHOLD && bestPrescription != null -> {
                ReferenceMatchResult(
                    prescription = bestPrescription,
                    similarity = bestSimilarity,
                    status = MatchStatus.MATCH,
                    matchedImagePath = bestMatchImage?.imagePath,
                    message = "Medicine matches your prescription for ${bestPrescription.medicineName}."
                )
            }
            bestSimilarity >= ReferenceMatcherConfig.UNCERTAIN_THRESHOLD && bestPrescription != null -> {
                ReferenceMatchResult(
                    prescription = bestPrescription,
                    similarity = bestSimilarity,
                    status = MatchStatus.UNCERTAIN,
                    matchedImagePath = bestMatchImage?.imagePath,
                    message = "Unable to confidently verify medicine (uncertain match with ${bestPrescription.medicineName})."
                )
            }
            else -> {
                ReferenceMatchResult(
                    prescription = prescriptionMap[targetPrescriptionId],
                    similarity = if (bestSimilarity > 0f) bestSimilarity else 0f,
                    status = MatchStatus.MISMATCH,
                    message = "Medicine image does not match your prescription reference."
                )
            }
        }
    }
}

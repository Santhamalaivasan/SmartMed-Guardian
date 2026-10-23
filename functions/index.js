const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Automatically triggers whenever a new notification document is written
 * to a patient's caregiver_notifications sub-collection:
 *   users/{patientUserId}/caregiver_notifications/{notificationId}
 */
exports.onCaregiverNotificationCreated = functions.firestore
  .document("users/{patientUserId}/caregiver_notifications/{notificationId}")
  .onCreate(async (snapshot, context) => {
    const notificationId = context.params.notificationId;
    const patientUserId = context.params.patientUserId;
    const data = snapshot.data();

    console.log("Notification trigger received", notificationId, "for patient:", patientUserId);

    if (!data) {
      console.error("No data found in notification snapshot");
      return null;
    }

    try {
      // 1. Retrieve the patient's caregiver configuration
      const caregiverDoc = await admin
        .firestore()
        .collection("users")
        .doc(patientUserId)
        .collection("caregiver")
        .doc("info")
        .get();

      let token = null;

      if (caregiverDoc.exists) {
        const caregiverData = caregiverDoc.data();
        token = caregiverData.caregiverFcmToken || caregiverData.fcmToken;
      }

      // Fallback: check patient's user document
      if (!token) {
        const userDoc = await admin
          .firestore()
          .collection("users")
          .doc(patientUserId)
          .get();
        if (userDoc.exists) {
          const userData = userDoc.data();
          token = userData.caregiverFcmToken || userData.fcmToken;
        }
      }

      if (!token) {
        console.warn("Caregiver token not found for patient:", patientUserId);
        await snapshot.ref.update({
          status: "failed_no_token",
          processedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
        return null;
      }

      console.log("Caregiver token found");

      // 2. Build the FCM message
      const message = {
        token: token,
        notification: {
          title: data.title || "Medication Taken",
          body: data.body || `${data.medicineName} was verified as taken.`,
        },
        data: {
          patientId: patientUserId,
          medicineName: String(data.medicineName || ""),
          status: String(data.status || ""),
          scheduledTime: String(data.scheduledTime || ""),
          actualTime: String(data.actualTime || ""),
          click_action: "com.smartmedguardian.ACTION_CAREGIVER_ALERT",
        },
        android: {
          priority: "high",
          notification: {
            channelId: "caregiver_notifications_channel",
            priority: "high",
            defaultSound: true,
            defaultVibrateTimings: true,
          },
        },
      };

      // 3. Send the FCM push notification automatically
      const response = await admin.messaging().send(message);
      console.log("FCM message sent", response);

      // 4. Mark as sent in Firestore
      await snapshot.ref.update({
        status: "sent",
        messageId: response,
        sentAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      return response;
    } catch (error) {
      console.error("Error sending caregiver FCM notification:", error);
      await snapshot.ref.update({
        status: "error",
        error: error.message || String(error),
        processedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      return null;
    }
  });

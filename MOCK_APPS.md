# MZBotanicals mock app

The `codex/mock-apps` branch contains the MZBotanicals demo app.

## AeroPay checkout

With valid checkout details, tapping **Pay by bank with AeroPay** opens the
success screen after saving a local demo order. The success screen identifies the
payment as a mock. If the save fails, checkout displays an error and can be retried.

This flow does not contact AeroPay, charge money, create a backend order, update
stock, or clear the cart. The cart stays available for repeated demonstrations.
Other app features, including authentication and the product catalog, still use
the existing backend. Demo orders are saved locally, separately for each signed-in
account, and persist across app restarts. They are not fulfillment requests and
do not sync to other devices.

## Orders

Open **Orders** from the drawer to see your newest demo orders first. Each order
contains its date, item quantities and prices captured at checkout, total, and
delivery details. Empty history offers **Browse products**; a load failure offers
**Try again**. Repeated taps on checkout do not save duplicate orders.

Keep this checkout implementation on the mock branch. Production checkout still
requires a real payment integration and verified payment confirmation.

## Manual check

### App branding

Install the debug APK and check the launcher icon in both circular and rounded
launcher layouts. Cold-launch the app: the system splash uses the green brand
logo, followed by the supplied portrait artwork fading into the app. Check a
portrait and landscape launch for centered artwork. Default profile pictures
and product image fallbacks now use the same brand logo.

### Profile photo

Open Profile and tap **Add profile photo**. Choose an image in the Android photo
picker and wait for confirmation. Verify the circular photo remains after leaving
Profile and reopening it, and after restarting the app. Use **Change photo** to
replace it and **Remove photo** to return to the default image. Canceling the
picker should leave the existing photo unchanged. Sign into another account and
verify its photo is independent. Offline or denied Firebase uploads should show
an error and preserve the previous photo. Photo changes save independently of
the profile form's Update button.

Photos use Firebase Storage under profile_images/<user ID>/<image ID>, with the
download URL stored in the customer's profileImageUrl field. Firebase billing
and Storage/Firestore rules must permit the signed-in user to save their own
photo. Desktop photo selection is not implemented.

### Focused product details

Open a product and tap its image, price, name, or description. Each opens a
dialog that brings the selected detail into focus. The image is shown without
cropping; long descriptions scroll. Close the dialog with **Close**, Android
Back, or a tap outside it. Verify the selected seed option and quantity remain
unchanged after closing, and that tapping another detail shows that detail.

### Demo checkout

1. Sign in, add a product to the cart, and open checkout.
2. Fill in valid checkout details; the AeroPay button becomes enabled.
3. Tap the AeroPay button. Confirm that the success screen appears with the mock
   payment message and no onboarding error.
4. Tap **Go Back** and verify the cart is still available for another demo.
5. Open **Orders** from the menu and check the items, total, and delivery details.
6. Restart the app and sign into the same account; confirm the order remains.
7. Sign into a different account and verify the first account's history is absent.
8. Leave a required checkout field invalid or use an empty cart; verify payment
   stays disabled. Enter a state name or its two-letter abbreviation.
9. Tap checkout repeatedly; confirm only one order is saved per checkout visit.

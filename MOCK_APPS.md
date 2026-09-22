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

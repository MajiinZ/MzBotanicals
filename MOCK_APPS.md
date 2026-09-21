# MZBotanicals mock app

The `codex/mock-apps` branch contains the MZBotanicals demo app.

## AeroPay checkout

With valid checkout details, tapping **Pay by bank with AeroPay** opens the
success screen without showing the AeroPay onboarding error. The success screen
identifies the payment as a mock.

This flow does not contact AeroPay, charge money, create an order, update stock,
or clear the cart. The cart stays available for repeated demonstrations.
Other app features, including authentication and the product catalog, still use
the existing backend; this branch only mocks the payment step.

Keep this checkout implementation on the mock branch. Production checkout still
requires a real payment integration and verified payment confirmation.

## Manual check

1. Sign in, add a product to the cart, and open checkout.
2. Fill in valid checkout details; the AeroPay button becomes enabled.
3. Tap the AeroPay button. Confirm that the success screen appears with the mock
   payment message and no onboarding error.
4. Tap **Go Back** and verify the cart is still available for another demo.
5. Leave a required checkout field invalid and verify payment stays disabled.

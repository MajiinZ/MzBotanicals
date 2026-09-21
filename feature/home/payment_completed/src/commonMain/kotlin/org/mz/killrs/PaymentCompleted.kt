package org.mz.killrs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mz.killrs.shared.Resources
import org.mz.killrs.shared.component.InfoCard
import org.mz.killrs.shared.component.PrimaryButton

@Composable
fun PaymentCompleted(
    navigateBack: () -> Unit,
    isSuccess: Boolean?,
    error: String?
){
    Column(
        modifier = Modifier.fillMaxSize()
            .systemBarsPadding()
            .padding(all = 24.dp)

    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ){
            InfoCard(
                title = if (isSuccess == true) "Success!" else "Oops",
                subtitle = if (isSuccess == true) {
                    "Mock payment completed. No payment was charged."
                } else {
                    error ?: "Something went wrong"
                },
                image = if (isSuccess == true) Resources.Image.KillrsLogo else Resources.Image.Delete
                )


        }
        PrimaryButton(
            text = "Go Back",
            icon = Resources.Icon.BackArrow,
            onClick = navigateBack
        )
    }

}
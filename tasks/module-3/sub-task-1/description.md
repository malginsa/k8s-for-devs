Step 1. Add helm chart to deploy your applications. Make replica-count and namespace a helm values.
Step 2. Add helm values file to store default values for helm variables.
Step 3. Run helm using helm install command to deploy applications with default helm variables. Make sure, your applications are up and running.
Step 4. Run helm once again, but this time set namespace and replica-count for helm intall to non-default values.

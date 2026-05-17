Step 1. Add Secrets object to your k8s manifest to store database username and password.
Step 2. Add config maps to store environment variables for application deployments.
Step 3. Add sql scripts to init databases (create tables) to config maps.
Step 4. Change k8s Deployment and StatefulSet objects to load these secrets and config-maps.

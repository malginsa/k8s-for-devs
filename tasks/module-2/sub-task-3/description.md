Add a field to one of your services, and perform Rolling-update deployment.

Step 1. To Song service add a new field genre (:String). Add corresponding logic so this field will represent genre of a song. This field also should be returned at the responses for both POST and GET operations.
Step 2. Build a new docker image of application with changes and push it to the Docker Hub (specify another version of container).
Step 3. Add Rolling-update deployment strategy to your deployments at manifest files and apply the  manifest, so the old versions of microservices are deployed and running.
Step 4. Set app version of app containers to the new one and apply manifest one more time. Make sure that new changes are deployed.

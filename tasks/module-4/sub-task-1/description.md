Step 1. Install Ingress-Nginx Controller using helm chart.
Step 2. Change Services type to ClusterIP to restrict external access.
Step 3. Create ingress resource and route your traffic using rules.
Step 4. Configure rewrite-target of path using annotations. Example routing: from http://localhost:8080/api/v1/songs to http://songs:8080/api/v1. Use this documentation: https://kubernetes.github.io/ingress-nginx/examples/rewrite/#rewrite-target 

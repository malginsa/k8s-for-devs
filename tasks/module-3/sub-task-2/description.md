Step 1. Create helm _helpers.tpl file and define next labels there:
  - current date : use helm generator for it's value
  - version
Step 2. Make config-map use values as labels from helm _helpers.tpl file.

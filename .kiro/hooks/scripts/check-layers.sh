#!/bin/sh
# PostFileSave hook: shared feature code may only use domain and model.
# Gradle enforces this for module edges; this catches stray imports right away.
file="$1"
case "$file" in
  *shared/feature/*.kt) ;;
  *) exit 0 ;;
esac
[ -f "$file" ] || exit 0

bad=$(grep -nE '^import com\.openbite\.core\.(data|network|database)\.' "$file")
if [ -n "$bad" ]; then
  echo "$file imports a layer below domain:" >&2
  echo "$bad" >&2
  echo "Add or reuse a use case in :shared:core:domain instead." >&2
  exit 1
fi
exit 0

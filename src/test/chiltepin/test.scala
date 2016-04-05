import ChiltepinImplicits._

Workflow realtime = true
Task create "wrf"
"wrf" runs "/home/Christopher.W.Harrop/test/test.sh"
"wrf" options "-A jetmgmt -l procs=1,partition=njet"
"wrf" environment Map("FOO" -> "FOO")

Task create "post"
"post" runs "/home/Christopher.W.Harrop/test/test.sh"
"post" options "-A jetmgmt -l procs=8,partition=sjet"
"post" environment Map("GOO" -> "GOO")

Workflow inspect

Workflow run


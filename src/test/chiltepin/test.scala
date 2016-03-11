import ChiltepinImplicits._

Workflow realtime = true
Task create "wrf"
"wrf" runs "foo"
"wrf" options "-A jetmgmt -l procs=1,partition=njet"
"wrf" environment Map("FOO" -> "FOO")

Workflow inspect

Workflow run


# AreaRollback

## Commands (OP-only)
`/rollback` \
List all backups in your `backups-dir` folder

`/rollback <backup name>` \
Roll back the selection to the selected backup

<details>
<summary>Extra fun commands</summary>

`/rollbackflipdimension` \
Flip the dimension copied by `/rollback`, `/rollbackfromself`

`/rollbackfromself` \
Roll back the selection using the servers own region files
</details>

## Technical notes
.r3
TODO: .zip files \
TODO: tile entities \
TODO: clearing of chunk cache, System.GC() ?






.zip
.7z
.tar*

config.txt
```
backupsDir=/mnt/vol1/Backups
```
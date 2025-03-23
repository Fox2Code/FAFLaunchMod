# FAFLaunch Mod

Auto updater and mod loader for the FAF Launcher

## Current APIs set

`${mymodid}.init.json`:

```json
{
  "events": "me.my_username.${mymodid}.MyEventListener"
}
```

`${mymodid}.mixins.json`:

See [the Mixin wiki](https://github.com/SpongePowered/Mixin/wiki)  
Also support [Mixin Extras](https://github.com/LlamaLad7/MixinExtras) and [Mixin Squared](https://github.com/Bawnorton/MixinSquared)

`${mymodid}.translations.json`:

```json
{
  "translation.key": "Translation value"
}
```

`${mymodid}.styles.css`: Inject JavaFX css to the launcher
`${mymodid}.settings.fxml`: Add entry to FAFLaunchMod setting page
`${mymodid}.tab.fxml`: Add tab to the FAF Launcher, translate `${mymodid}.tab` to set tab name.

## Notes

Official FAF Launcher: <https://github.com/FAForever/downlords-faf-client>

FAFLaunch Mod is currently not very clean code.
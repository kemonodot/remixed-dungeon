local buff = require "scripts/lib/buff"
return buff.init{
    id = "ProofAftershock",
    tags = {"DEATH", "AOE", "DAMAGE", "CHAIN"},
    trigger = "DEATH",
    condition = function(bridge, event) return event:isEnemyDeath() end,
    effect = function(bridge, event) bridge:blast(event:getCell(), 3, 1) end,
    desc = function()
        return {icon=18, name="Death Aftershock", info="An enemy death deals 3 special damage to nearby enemies. Deaths can chain; chain depth and activation count are bounded."}
    end,
    act = function(self, marker) marker:spend(1) end
}

local buff = require "scripts/lib/buff"
return buff.init{
    id = "ProofRejection",
    tags = {"ATTACK", "KNOCKBACK", "POSITION"},
    trigger = "HIT",
    condition = function(bridge, event)
        return bridge:isHero(event:getActor()) and bridge:isEnemy(event:getTarget())
    end,
    effect = function(bridge, event) bridge:knockBack(event:getTarget()) end,
    desc = function()
        return {icon=1, name="Rejection", info="A landed ordinary attack deals at most 1 damage and pushes its target one tile. Walls and occupied tiles block the push."}
    end,
    act = function(self, marker) marker:spend(1) end
}

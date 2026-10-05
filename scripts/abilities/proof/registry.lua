local registry = {}
registry.definitions = {
    require "scripts/buffs/ProofSparks",
    require "scripts/buffs/ProofRejection",
    require "scripts/buffs/ProofAftershock"
}

function registry.dispatch(self, bridge, event)
    for _, ability in ipairs(self.definitions) do
        if ability.trigger == event:getType() and bridge:owns(ability.id)
            and ability.condition(bridge, event) and bridge:reserveActivation(ability.id) then
            ability.effect(bridge, event)
        end
    end
end

function registry.metadata(self, bridge)
    local result = {}
    for _, ability in ipairs(self.definitions) do
        result[#result+1] = {id=ability.id, trigger=ability.trigger, tags=ability.tags}
    end
    return result
end

return registry

const mustHaveVolumes = [
    "Multimedia2",
    "Proxies2",
    "StudioPipe2"
];

function validateMdcPing(mdcPing){
    const visibleMdcList = mdcPing.filter(entry=>entry.visible);
    if(visibleMdcList.length===0){
        return "problem";
    }

    if(visibleMdcList.length!==mdcPing.length){
        return "warning";
    }

    const highPacketCounts = mdcPing.filter(entry=>entry.packetloss>0);
    if(highPacketCounts.length>0){
        return "warning";
    }
    return "normal";
}

function validateRecord(record){
    console.debug("validateRecord", record);
    if(record.model!=="Mac Pro" && record.model!=="Mac Studio"){
        return "unimportant";
    }

    if(record.ipAddresses.length<2){
        console.log(record.hostName + " has no metadata network");
        return "unimportant";
    }

    if(record.fcWWN.length<2){
        console.log(record.hostName + " has insufficient fibre interfaces");
        return "problem";
    }

    if(!record.mdcPing) return "warning";
    const mdcStatus = validateMdcPing(record.mdcPing);
    if(mdcStatus!=="normal") return mdcStatus;


    const actualLunCount = record.fcLunCount.filter(entry=>entry>0);

    if(actualLunCount[0]<20){
        console.log(record.hostName + " only has " + actualLunCount + " LUNs visible (expected at least 20)");
        return "problem";
    }

    if(record.model=="Mac Studio") {
        if (!record.denyDlcVolumes) return "warning";
        if (record.denyDlcVolumes[0] != "false") return "warning";
    }

    const sanMountsNames = record.sanMounts.map(entry=>entry.name);
    if(sanMountsNames.length<mustHaveVolumes.length) return "warning";

    if(!record.sanMounts) return "warning";

    return "normal";
}

export {validateRecord};
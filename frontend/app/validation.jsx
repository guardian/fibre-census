const mustHaveVolumes = [
    "Multimedia2",
    "Proxies2",
    "StudioPipe2"
];

function validateRecord(record){
    console.debug("validateRecord", record);
    if(record.model!=="Mac Pro"){
        return "unimportant";
    }

    if(record.ipAddresses.length<2){
        console.log(record.hostName + " has no metadata network");
        return "unimportant";
    }

    if(record.fcWWN.length<2){
        console.log(record.hostName + " has insufficient fibre interfaces");
        return "warning";
    }

    const actualLunCount = record.fcLunCount.filter(entry=>entry>0);
    if(actualLunCount[0]===120){
        return "warning";
    }

    if(actualLunCount[0]!==20){
        console.log(record.hostName + " only has " + actualLunCount + " LUNs visible (expected 20)");
        return "warning";
    }

    if(!record.denyDlcVolumes) return "info";
    if(record.denyDlcVolumes.length<mustHaveVolumes.length) return "info";

    if(mustHaveVolumes.filter(entry=>!record.denyDlcVolumes.includes(entry)).length>0) return "info";
    return "normal";
}

export {validateRecord};
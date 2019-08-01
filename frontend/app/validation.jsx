const mustHaveVolumes = [
    "Multimedia2",
    "Proxies2",
    "StudioPipe2"
];

function validateMdcPing(mdcPing){
    const visibleMdcList = mdcPing.filter(entry=>entry.visible);
    if(visibleMdcList.length===0){
        //this.setState({tooltip: "No metadata controllers visible"});
        return "warning";
    }

    if(visibleMdcList.length!==mdcPing.length){
        //this.setState({tooltip: "Not all metadata controllers visible"});
        return "info";
    }

    const highPacketCounts = mdcPing.filter(entry=>entry.packetloss>0);
    if(highPacketCounts.length>0){
        //this.setState({tooltip: "Packet loss seen, check network connections"});
        return "info";
    }
    return "normal";
}

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

    if(!record.mdcPing) return "info";
    const mdcStatus = validateMdcPing(record.mdcPing);
    if(mdcStatus!=="normal") return "normal";


    const actualLunCount = record.fcLunCount.filter(entry=>entry>0);

    if(actualLunCount[0]!==20){
        console.log(record.hostName + " only has " + actualLunCount + " LUNs visible (expected 20)");
        return "warning";
    }

    if(!record.denyDlcVolumes) return "info";
    if(record.denyDlcVolumes.length<mustHaveVolumes.length) return "info";

    const sanMountsNames = record.sanMounts.map(entry=>entry.name);
    if(sanMountsNames.length<mustHaveVolumes.length) return "info";

    if(!record.sanMounts) return "info";


    if(mustHaveVolumes.filter(entry=>!record.denyDlcVolumes.includes(entry)).length>0) return "info";
    return "normal";
}

export {validateRecord};
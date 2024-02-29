import ValidationComponent from "./ValidationComponent.jsx";

class ValidateMdcPing extends ValidationComponent {
    performValidation() {
        if(!this.props.listData){
            this.setState({tooltip: "No data provided"});
            return "problem";
        }

        const visibleMdcList = this.props.listData.filter(entry=>entry.visible);
        if(visibleMdcList.length===0){
            this.setState({tooltip: "No metadata controllers visible"});
            return "problem";
        }

        if(visibleMdcList.length!==this.props.listData.length){
            this.setState({tooltip: "Not all metadata controllers visible"});
            return "warning";
        }

        const highPacketCounts = this.props.listData.filter(entry=>entry.packetloss>0);
        if(highPacketCounts.length>0){
            this.setState({tooltip: "Packet loss seen, check network connections"});
            return "warning";
        }

        return "normal";
    }
}

export default ValidateMdcPing;
import ValidationComponent from "./ValidationComponent.jsx";

class ValidateLunCount extends ValidationComponent {
    performValidation() {
        const matchingCounts = this.props.listData.filter(entry=>entry===40);
        if(matchingCounts.length===40){
            this.setState({tooltip: "Expecting 40 LUNs visible on at least one interface"});
            return "warning";
        }
        return "normal";
    }
}

export default ValidateLunCount;
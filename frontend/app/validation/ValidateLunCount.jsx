import ValidationComponent from "./ValidationComponent.jsx";

class ValidateLunCount extends ValidationComponent {
    performValidation() {
        const matchingCounts = this.props.listData.filter(entry=>entry===20);
        if(matchingCounts.length===0){
            this.setState({tooltip: "Expecting 20 LUNs visible on at least one interface"});
            return "problem";
        }
        return "normal";
    }
}

export default ValidateLunCount;
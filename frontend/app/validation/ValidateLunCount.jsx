import ValidationComponent from "./ValidationComponent.jsx";

class ValidateLunCount extends ValidationComponent {
    performValidation() {
        const matchingCounts = this.props.listData.filter(entry=>entry>19);
        if(matchingCounts.length===0){
            this.setState({tooltip: "Expecting at least 20 LUNs visible on at least one interface"});
            return "problem";
        }
        return "normal";
    }
}

export default ValidateLunCount;
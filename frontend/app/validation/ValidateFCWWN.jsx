import ValidationComponent from "./ValidationComponent.jsx";

class ValidateFCWWN extends ValidationComponent {
    performValidation() {

        if(this.props.listData.length<2){
            this.setState({tooltip: "Insufficient fibre interfaces, expecting at least 2"});
            return "problem";
        }
        return "normal"
    }
}

export default ValidateFCWWN;
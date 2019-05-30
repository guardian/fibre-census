import React from "react";
import PropTypes from "prop-types";

class SortSelector extends React.Component {
    static propTypes = {
        value: PropTypes.string.isRequired,
        order: PropTypes.string.isRequired,
        onChange: PropTypes.func.isRequired
    };

    constructor(props){
        super(props);

        this.selectorChanged = this.selectorChanged.bind(this);
        this.radioChanged = this.radioChanged.bind(this);
    }

    selectorChanged(evt){
        this.props.onChange(evt.target.value, this.props.order);
    }

    radioChanged(evt){
        this.props.onChange(this.props.value, evt.target.value)
    }

    render(){
        return <span>
                <select value={this.props.value} onChange={this.selectorChanged}>
                    <option value="time">Last updated</option>
                    <option value="hostname">Hostname</option>
                </select>
                <label><input type="radio" name="order-selector" value="ascending" checked={this.props.order==="ascending"} onChange={this.radioChanged}/>Ascending</label>
                <label><input type="radio" name="order-selector" value="descending" checked={this.props.order==="descending"} onChange={this.radioChanged}/>Descending</label>
                </span>
    }
}

export default SortSelector;
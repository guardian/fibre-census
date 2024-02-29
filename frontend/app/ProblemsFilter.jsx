import React from 'react';
import PropTypes from 'prop-types';

/**
 * unimportant - circle-minus
 * problem      - exclamation-triangle
 * warning      - exclamation-circle
 * normal       - nothing
 */

class ProblemsFilter extends React.Component {
    static propTypes = {
        value: PropTypes.string,
        onChange: PropTypes.func.isRequired
    };

    render(){
        return <select onChange={this.props.onChange} value={this.props.value}>
            <option value="all">(all)</option>
            <option value="unimportant">Unimportant</option>
            <option value="problem">With problems</option>
            <option value="warning">With warnings</option>
            <option value="normal">OK only</option>
        </select>
    }
}

export default ProblemsFilter;